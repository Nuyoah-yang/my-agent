# 第三阶段：入库模块（in）实战学习
## 覆盖范围：Day 8–10

> 目标：能看懂入库全流程代码，能在数据库里追踪每一步操作的痕迹。

**参考文档：** `说明/入库模块(in)详细说明.md`

---

## Day 8：入库枚举与状态机

> 不懂枚举，看业务代码只会一头雾水。先把枚举背熟再读代码。

### 第一步：一次性搞懂所有入库枚举（1.5 小时）

**打开以下文件，逐个阅读：**

**1. `EReceiptStatus.java`（到货通知/收货单状态）**
```
common-operation/src/main/java/com/hongtu/tz/in/enums/EReceiptStatus.java
```
```java
CREATE(1, "新建"),
PROCESSING(2, "收货中"),
FINISHED(3, "收货完成"),
CLOSED(4, "已关单"),
CANCELED(5, "已取消"),
```

**状态流转规则（画在纸上）：**
```
1(新建) ──confirm()──→ 2(收货中) ──inspectAccept()──→ 3(收货完成)
   │                       │
cancel()               close()（质检不合格时）
   ↓                       ↓
5(已取消)              4(已关单)
```

**2. `EConfirmStatus.java`（确认状态）**
```
WAIT_CONFIRM(1, "未确认")  →  confirm() →  CONFIRMED(2, "已确认")
```

**3. `EInspectAcceptStatus.java`（验收状态）**
```
NOT_INSPECTED(1, "未验收")  →  inspectAccept() →  INSPECTED(2, "已验收")
                                                 →  PART_INSPECTED(3, "部分验收")
```

**4. `EQualifyResult.java`（质检结果）**
```
PASS(1, "合格")   FAIL(2, "不合格")
```

**5. `EOrderType.java`（订单类型，最重要！）**
```
common-operation/src/main/java/com/hongtu/tz/in/enums/EOrderType.java
```

| 订单类型 | code | 需要质检 | 需要验收 | 说明 |
|---------|------|---------|---------|------|
| YYCG | 原药采购入库 | ✅ | ✅ | 走完整流程 |
| FCCG | 辅材采购入库 | ✅ | ✅ | 走完整流程 |
| BCCG | 包材采购入库 | ✅ | ✅ | 走完整流程 |
| ZJPRK | 中间品生产入库 | ❌ | ✅ | 不需质检，需验收 |
| CPRK | 成品生产入库 | ❌ | ❌ | 直接完成 |
| ZYKRK | 资源库入库 | ❌ | ❌ | 适合一键收货 |
| DBRK | 调拨入库单 | ✅ | ✅ | 走完整流程 |

**验证：** 搜索代码中如何使用 `needQualityInspect`：
```java
// 在 ArrivalNoticeServiceImpl.inspectAccept() 中找到这段逻辑
byIds.stream()
    .filter(o -> Optional.ofNullable(EOrderType.getEnum(o.getOrderType()))
                         .map(EOrderType::getNeedQualityInspect)
                         .orElse(true)  // 如果找不到类型，默认需要质检（安全起见）
                 && o.getQualifyResult() == null)  // 且还没质检结果
    .findAny()
    .ifPresent(o -> {
        throw new ServiceException("订单[" + o.getArrivalNoticeNo() + "]质检还未完成，不能验收");
    });
```

### 第二步：在数据库里看真实数据（1 小时）

```sql
-- 查看各种订单类型的数量
SELECT order_type, COUNT(*) cnt, COUNT(DISTINCT order_status) status_variety
FROM wms_arrival_notice
GROUP BY order_type
ORDER BY cnt DESC;

-- 找一张完整走完流程的到货通知单
SELECT
    id, arrival_no, order_type,
    order_status,
    confirm_status,
    inspect_accept_status,
    qualify_result,
    planned_quantity, received_quantity, inspect_accept_quantity
FROM wms_arrival_notice
WHERE order_status = 3          -- 收货完成
  AND inspect_accept_status = 2 -- 已验收
LIMIT 5;

-- 记录这张单的各个字段值，对照枚举理解含义
```

### ✅ Day 8 上午验收
- [ ] 能画出 `EReceiptStatus` 的状态流转图
- [ ] 能说出 `ZYKRK` 和 `YYCG` 订单类型的区别
- [ ] 知道 `needQualityInspect` 字段在代码中哪里被使用

### 第三步：读 `ArrivalNoticeController`（1 小时）

**打开文件：**
```
common-operation/src/main/java/com/hongtu/tz/in/controller/ArrivalNoticeController.java
```

快速浏览所有方法，对照 `说明/入库模块(in)详细说明.md` 的接口表，确认每个接口都能找到。

**重点关注以下方法的参数类型，理解数据怎么传进来：**

```java
// 一键收货接口
@PostMapping("oneKeyReceive")
public AjaxResult oneKeyReceive(@Validated @RequestBody OneKeyReceiveVO vo) {
    // OneKeyReceiveVO 包含：单据id列表 + 仓库编码 + 库区编码 + 库位编码
    arrivalNoticeService.oneKeyReceive(vo.getIds(), vo.getStoreCode(), vo.getAreaCode(), vo.getPosCode());
    return success();
}

// 验收接口
@PostMapping("acceptance")
public AjaxResult inspect(@RequestBody AcceptDTO acceptDTO) {
    // AcceptDTO 包含：到货通知单id列表 + 水分备注 + 按行验收时间（可选）
    arrivalNoticeService.inspectAccept(acceptDTO);
    return success();
}
```

**打开 VO 类，看字段：**
```
common-operation/src/main/java/com/hongtu/tz/in/controller/vo/OneKeyReceiveVO.java
```

---

## Day 9：深度读核心 Service 方法

### 第一步：读 `confirm` 方法（45 分钟）

**打开文件：**
```
common-operation/src/main/java/com/hongtu/tz/in/service/impl/ArrivalNoticeServiceImpl.java
```

找到 `confirm` 方法（大约第 829 行），逐行阅读，在旁边加注释理解：

```java
public void confirm(List<Long> list, Date confirmTime) {
    // ① 查出所有要确认的到货通知单
    List<ArrivalNoticePO> byIds = listByIds(list);
    Assert.notEmpty(byIds, "确认失败，没有找到订单");

    // ② 防御性校验：已确认的不能再次确认
    byIds.stream()
        .filter(o -> EConfirmStatus.CONFIRMED.getCode().equals(o.getConfirmStatus()))
        .findAny()
        .ifPresent(o -> { throw new ServiceException("...已确认..."); });

    // ③ 防御性校验：已取消的不能确认
    byIds.stream()
        .filter(o -> EReceiptStatus.CANCELED.getCode().equals(o.getOrderStatus()))
        .findAny()
        .ifPresent(o -> { throw new ServiceException("...已取消..."); });

    // ④ 为每张到货通知单生成一个"收货任务"
    List<WmsTaskPO> taskPOList = byIds.stream().map(o -> {
        WmsTaskPO wmsTaskPO = new WmsTaskPO();
        wmsTaskPO.setTaskType(ETaskType.RECEIPT.getCode());  // 任务类型=收货
        wmsTaskPO.setBusinessDocNo(o.getArrivalNoticeNo());  // 关联到货通知单号
        return wmsTaskPO;
    }).collect(Collectors.toList());
    wmsTaskService.batchInsert(taskPOList);   // ← 写入 wms_task 表

    // ⑤ 更新主单状态为"已确认"
    lambdaUpdate().in(BasePO::getId, list)
        .set(ArrivalNoticePO::getConfirmStatus, EConfirmStatus.CONFIRMED.getCode())
        .set(ArrivalNoticePO::getConfirmTime, ...)
        .update();
}
```

**在数据库验证：**
```sql
-- 确认操作后，wms_task 表会有新记录
SELECT task_no, task_type, status, business_doc_no
FROM wms_task
WHERE task_type = 1   -- 1=收货任务（ETaskType.RECEIPT）
  AND business_doc_no = '你的到货通知单号'
LIMIT 5;
```

### 第二步：读 `inspectAccept` 方法（1.5 小时）——最重要！

这是入库模块中最复杂的方法，逐步拆解：

**阶段 1：前置校验（读懂每个校验的意义）**

```java
// 校验 1：已取消不能验收
byIds.stream().filter(o -> CANCELED.equals(o.getOrderStatus()))...

// 校验 2：已关单不能验收
byIds.stream().filter(o -> CLOSED.equals(o.getOrderStatus()))...

// 校验 3：已验收不能重复验收
byIds.stream().filter(o -> INSPECTED.equals(o.getInspectAcceptStatus()))...

// 校验 4：未收货不能验收（必须先收货才能验收）
byIds.stream().filter(o -> CREATE.equals(o.getOrderStatus()))...

// 校验 5：需要质检的订单，质检未完成不能验收
byIds.stream().filter(o ->
    EOrderType.getEnum(o.getOrderType()).getNeedQualityInspect() // 这类订单需要质检
    && o.getQualifyResult() == null                              // 且质检结果还没有
)...

// 校验 6：质检不合格不能验收
byIds.stream().filter(o -> FAIL.equals(o.getQualifyResult()))...
```

**阶段 2：核心操作**

```java
// 查询所有"收货中"且"未验收"的收货明细
List<ReceiptDetailPO> receiptDetailPOList = receiptDetailService.getReceiveDetail(q);

// 更新收货明细：已验收 + 收货完成
receiptDetailPOList.forEach(rd -> {
    rd.setInspectAcceptStatus(INSPECTED.getCode());  // 验收状态=已验收
    rd.setReceiptStatus(FINISHED.getCode());          // 收货状态=完成
    rd.setInspectAcceptQuantity(rd.getReceivedQuantity());
});
receiptDetailService.updateBatchById(receiptDetailPOList);

// 更新收货单状态
receiptService.lambdaUpdate()...set(ReceiptPO::getOrderStatus, FINISHED.getCode()).update();

// 更新到货通知单明细的验收数量
arrivalNoticeDetailService.lambdaUpdate()
    .setSql("inspect_accept_quantity = inspect_accept_quantity + {0}", quantity)...

// ⭐ 关键步骤：生成上架单 + 触发库存入账
generatePutawayOrder(receiptPOList);

// 更新到货通知主单状态为"收货完成"
lambdaUpdate()...set(ArrivalNoticePO::getOrderStatus, FINISHED.getCode())...

// 异步通知 ERP 收货完成
receiptPOList.forEach(o -> openThreadSyncReceiptFinish(o, ...));
```

**在数据库验证验收后的变化：**
```sql
-- 验收后，到货通知单主表应该变为 order_status=3, inspect_accept_status=2
SELECT order_status, inspect_accept_status, inspect_accept_quantity
FROM wms_arrival_notice WHERE id = 你的ID;

-- 验收后，应该生成上架单
SELECT * FROM wms_putaway WHERE arrival_notice_id = 你的ID;

-- 验收后，库存表应该有新记录（在收货库位）
SELECT pos_code, inventory_quantity, available_quantity, locked_quantity
FROM wms_inventory
WHERE id IN (
    SELECT inventory_id FROM wms_inventory_trans_detail
    WHERE trans_doc_no LIKE 'R%'  -- 收货单号
    AND trans_type = 1
);
```

### 第三步：读 `generatePutawayOrder` 方法（45 分钟）

```java
public void generatePutawayOrder(List<ReceiptPO> receiptList) {

    // 每个收货单可能生成多个上架单（按件型拆分）
    receiptList.forEach(receipt -> {

        // ① 生成上架单（整件/散件/零件分别建单）
        List<PutawayPO> putawayPOList = buildPutawayPO(receipt);
        putawayService.saveBatch(putawayPOList);  // 写入 wms_putaway

        // ② 生成上架单明细
        putawayDetailService.saveBatch(putawayDetailPOList);  // 写入 wms_putaway_detail

        // ③ 生成上架任务
        List<WmsTaskPO> wmsTaskPOList = ...;
        wmsTaskService.batchInsert(wmsTaskPOList);  // 写入 wms_task

        // ⭐ ④ 最关键：调用库存入库服务，货物正式入账！
        inventoryInService.arrival(buildInventoryArrivalCommand(receipt, putawayDetailPOList));
        //   ↑ 这行调用会：
        //   a. 在收货库位创建 wms_inventory 记录（inventoryQuantity = 收货数量）
        //   b. 写入 ARRIVAL_IN 流水记录
        //   c. 为每条上架明细创建锁定记录（lockType=上架锁定）
        //   d. 相应减少 availableQuantity（被上架锁定了）
    });

    // 将取样记录标记为已扣减（零件上架相关）
    qualitySamplingService.lambdaUpdate()
        .set(QualitySamplingPO::getIsDeducted, true).update();
}
```

**理解 `buildInventoryArrivalCommand`：**

按 Ctrl + 左键点击 `buildInventoryArrivalCommand`，找到这个方法，看它如何构建命令对象：
- 从收货明细中提取货品、批号、数量、成本单价等信息
- 验证"上架计划总量 = 收货总量"（数量不能凭空多出来）
- 组装 `InventoryArrivalCommand` 对象传给库存服务

### 第四步：读 `oneKeyReceive` 方法（30 分钟）

对比 `oneKeyReceive` 和 `inspectAccept+generatePutawayOrder` 的差异：

```java
public void oneKeyReceive(List<Long> list, String storeCode, String areaCode, String posCode) {
    // 前置校验（比 inspectAccept 少了质检相关的校验）
    // ...

    // 直接把到货明细状态改为"收货完成+已验收"
    arrivalNoticeDetailService.lambdaUpdate()
        .set(ArrivalNoticeDetailPO::getReceiptStatus, FINISHED.getCode())
        .set(ArrivalNoticeDetailPO::getInspectAcceptStatus, INSPECTED.getCode())
        .setSql("received_quantity = planned_quantity")          // 实收=计划量
        .setSql("inspect_accept_quantity = planned_quantity")   // 验收量=计划量
        .update();

    // 完成收货任务
    wmsTaskService.finish(arrivalNoticeNoList, now);

    // ⭐ 调用一键入库（不是 arrival，而是 processOneClickArrival）
    oneKeyInbound(receiptPOList, posByCode);
    // 内部调用：inventoryInService.processOneClickArrival()
    // 区别：processOneClickArrival 不需要上架环节，货物直接落到指定库位
}
```

**与正常流程的关键区别：**

| 对比项 | 正常流程 | 一键收货 |
|--------|---------|---------|
| 质检 | 可能需要 | 跳过 |
| 验收 | 需要人工确认 | 自动设为已验收 |
| 上架单 | 生成上架单，PDA 再执行上架 | 不生成上架单，直接入目标库位 |
| 库存操作 | `arrival()`：先在收货库位入账，再锁定待上架 | `processOneClickArrival()`：直接在目标库位入账 |

---

## Day 10：入库综合练习日

### 上午（2 小时）：追踪一个完整入库流程的数据

选择数据库中已有的一张完整入库单，按顺序查每一步产生的数据：

```sql
-- 第 1 步：找一张完整流程的到货通知单
SELECT id, arrival_no, order_type, order_status, confirm_status,
       inspect_accept_status, qualify_result
FROM wms_arrival_notice
WHERE order_type = 'YYCG'      -- 原药采购（走完整质检+验收流程）
  AND order_status = 3          -- 收货完成
  AND inspect_accept_status = 2 -- 已验收
LIMIT 3;

-- 假设找到 id=100, arrival_no='A202604300001'

-- 第 2 步：查收货任务（confirm 操作产生）
SELECT task_no, task_type, status FROM wms_task
WHERE business_doc_no = 'A202604300001' AND task_type = 1;

-- 第 3 步：查质检单（若 needQualityInspect=true）
SELECT id, inspection_no, status, qualify_result
FROM wms_quality_inspection
WHERE arrival_notice_id = 100;

-- 第 4 步：查收货单（PDA 扫码收货产生）
SELECT id, receipt_no, order_status FROM wms_receipt
WHERE arrival_notice_id = 100;

-- 第 5 步：查上架单（inspectAccept 产生）
SELECT id, putaway_no, putaway_type, status FROM wms_putaway
WHERE arrival_notice_id = 100;
-- putaway_type: 1=整件, 2=散件, 3=零件（可能有多张）

-- 第 6 步：查库存变动（inventoryInService.arrival 产生）
SELECT trans_type, trans_quantity, trans_doc_no, operation_time
FROM wms_inventory_trans_detail
WHERE trans_doc_no LIKE 'R%'  -- 关联收货单号
ORDER BY id;
-- 应该能看到 trans_type=1（收货入库）

-- 第 7 步：查当前库存（上架完成后的最终状态）
SELECT pos_code, goods_code, batch_no,
       inventory_quantity, available_quantity, locked_quantity
FROM wms_inventory
WHERE id IN (
    SELECT inventory_id FROM wms_inventory_trans_detail
    WHERE trans_doc_no LIKE (
        SELECT CONCAT(putaway_no, '%') FROM wms_putaway WHERE arrival_notice_id = 100 LIMIT 1
    )
    AND trans_type = 5  -- 上架移入
);
```

**把你找到的数据填入下表：**

| 步骤 | 生成的记录 | 表名 | 关键字段值 |
|------|---------|------|---------|
| confirm | 收货任务 | wms_task | task_no=___ |
| 质检 | 质检单 | wms_quality_inspection | qualify_result=___ |
| 收货 | 收货单 | wms_receipt | receipt_no=___ |
| inspectAccept | 上架单 | wms_putaway | putaway_no=___ |
| arrival() | 库存记录 | wms_inventory | inventoryQty=___ |
| arrival() | 入库流水 | wms_inventory_trans_detail | trans_type=1 |
| 上架完成 | 上架流水 | wms_inventory_trans_detail | trans_type=5/6 |

### 下午（2 小时）：自测题

**不看文档，凭理解回答以下问题：**

**题目 1：** 一张 `YYCG`（原药采购）订单，从新建到收货完成，必须经历哪些步骤？
> 参考答案：新建 → confirm（确认）→ PDA 收货 → 质检取样 → 质检完成（合格）→ inspectAccept（验收）→ 生成上架单 → PDA 上架 → 收货完成

**题目 2：** `generatePutawayOrder` 方法调用完之后，`wms_inventory` 表里会有记录吗？`available_quantity` 是多少？
> 参考答案：有记录！但 `available_quantity` = 0，因为全部数量都被"上架锁定"了（lockedQuantity=总量）。货物在收货库位等待被上架，还不能被出库使用。

**题目 3：** 如果 PDA 上架了 50 箱到目标库位，这时候 `wms_inventory` 表会有哪些变化？
> 参考答案：
> 1. 收货库位的记录：inventoryQuantity 减少 50，lockedQuantity 减少 50（解锁）
> 2. 目标库位的记录：inventoryQuantity 增加 50，availableQuantity 增加 50（可以出库了！）
> 3. 流水：收货库位写 trans_type=6（上架移出），目标库位写 trans_type=5（上架移入）

**题目 4：** 撤销收货时，如果上架单已经"执行中"（部分已上架），能直接撤销吗？
> 参考答案：不能直接撤销，`revokeInventory` 会判断上架状态，已经执行过的上架需要先处理。参考 `ArrivalNoticeServiceImpl.revokeReceipt` 的三种分支逻辑（待上架/已上架/无上架单）。

**题目 5（挑战题）：** 找到代码中处理"撤销收货"的方法，说出它的三个分支是什么。
> 参考答案：在 `revokeInventory` 方法中：
> - 分支 1：有上架单且状态=待上架 → cancelPutaway（取消上架锁定）
> - 分支 2：有上架单且已上架 → 涉及复杂的库存回滚
> - 分支 3：没有上架单 → cancelArrival（直接冲销收货入库）

### ✅ Day 10 最终验收

完成以下检查，全部打勾才算过关：

- [ ] 能在数据库中找到一张完整入库单的所有关联数据（7 张表）
- [ ] 能解释 `inspectAccept` 完成后 `wms_inventory` 的 3 个数量字段是什么值
- [ ] 能说出一键收货和正常流程在库存操作上的本质区别
- [ ] 能找到 `revokeReceipt` 方法并说出它的三个分支
- [ ] 能从流水表反推某次操作是什么业务触发的

---

## 第三阶段总结

学完入库模块后，你掌握了：

1. **入库的完整状态机**：7 种状态，每个接口对应的状态变化
2. **库存触发时机**：`inspectAccept` → `generatePutawayOrder` → `inventoryInService.arrival()` 才真正入账
3. **两种入库路径**：标准流程 vs 一键收货，适用场景不同
4. **防御性编程模式**：大量的前置校验，保证状态合法性

> 下一步（第四阶段）：带着库存知识，去看出库模块如何从库存中"取钱"（分配、拣货、发货）。