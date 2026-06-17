# 第二阶段：库存核心模块（io）深度学习
## 覆盖范围：Day 6–7

> ⭐ 这是整个 WMS 系统最核心的部分，务必吃透再进行后续学习！
> 目标：理解库存如何记账，能看懂任何操作对库存产生的影响。

---

## 为什么先学 io 模块？

很多实习生急着看入库、出库，结果遇到 `inventoryInService.arrival()` 就不知道它做了什么。
**先学 io 模块，后面所有业务模块都会豁然开朗。**

类比：io 模块就像银行的"账本系统"，入库就是存钱，出库就是取钱，io 记录了每一笔变动。

---

## Day 6：库存账本设计深度理解

### 第一步：理解 `wms_inventory` 表（核心！）（1 小时）

**打开文件：**
```
common-operation/src/main/java/com/hongtu/tz/io/base/domain/Inventory.java
```

这是最重要的实体类，一行记录代表"某个货品在某个库位的库存状态"。

**最关键的 3 个数量字段：**

```java
// 库存总量
private BigDecimal inventoryQuantity;   // 这个库位的总库存数量

// 可用量（可以被出库的量）
private BigDecimal availableQuantity;   // = inventoryQuantity - lockedQuantity

// 锁定量（已被占用，暂不可用）
private BigDecimal lockedQuantity;      // 被出库分配/上架等操作锁定的数量
```

**核心等式（铁律，永远成立）：**
```
inventoryQuantity = availableQuantity + lockedQuantity
```

**理解这张表的"唯一键"（以下字段组合唯一标识一条库存记录）：**
```java
private String storeCode;      // 仓库（哪个仓库）
private String ownerCode;      // 货主（谁的货）
private String storageCode;    // 保管账（ERP 账户维度）
private String areaCode;       // 库区
private String posCode;        // 库位（具体在哪个格子）
private String goodsCode;      // 货品（什么货）
private String packCode;       // 包装规格
private String batchNo;        // 批号（同一货品不同批次分开记）
```

**在数据库验证：**
```sql
-- 查看库存数据，观察 3 个数量字段
SELECT store_code, goods_code, batch_no, pos_code,
       inventory_quantity,
       available_quantity,
       locked_quantity,
       (inventory_quantity - available_quantity - locked_quantity) AS diff  -- 应该全是 0
FROM wms_inventory
LIMIT 20;

-- 验证核心等式（结果应该是 0 行，即没有不一致的数据）
SELECT COUNT(*) AS inconsistent_count
FROM wms_inventory
WHERE ABS(inventory_quantity - available_quantity - locked_quantity) > 0.001;
```

> 如果 diff 不为 0，说明数据有问题（并发 bug 或手动改了数据库）。这个等式是系统数据完整性的保障。

### 第二步：理解锁定的概念（30 分钟）

**锁定**：库存被某个业务"预约"了，还没真正出去，但不能被其他业务再用。

**打开枚举文件：**
```
common-operation/src/main/java/com/hongtu/tz/io/base/enums/ELockType.java
```

```java
PUTAWAY(1, "上架"),      // 收货后等待上架，这部分库存被锁定（不能出库）
OUTBOUNT(2, "出库"),     // 出库单分配了库存，等待拣货（不能再分给别的订单）
MOVEMENT(3, "移动"),     // 库内移动中的库存
TRANSFER(4, "移库"),     // 跨仓移库中的库存
MAINTENANCE(5, "养护"),  // 养护计划中的库存
```

**数据库验证：**
```sql
-- 查看当前有哪些锁定记录
SELECT wil.lock_type, wil.lock_doc_no, wil.locked_quantity,
       wi.goods_code, wi.pos_code
FROM wms_inventory_lock wil
LEFT JOIN wms_inventory wi ON wil.inventory_id = wi.id
LIMIT 20;

-- 查看某条库存的锁定明细
SELECT * FROM wms_inventory_lock
WHERE inventory_id = (SELECT id FROM wms_inventory LIMIT 1);
```

**场景举例：**

假设库位 `A-01-01` 有货品 `G001` 共 100 箱：
- 入库放进来时：`inventoryQuantity=100, availableQuantity=100, lockedQuantity=0`
- 出库单分配了 30 箱时：`inventoryQuantity=100, availableQuantity=70, lockedQuantity=30`
- 拣货完成发货后：`inventoryQuantity=70, availableQuantity=70, lockedQuantity=0`

### 第三步：理解库存流水（45 分钟）

**打开枚举文件：**
```
common-operation/src/main/java/com/hongtu/tz/io/base/enums/ETransType.java
```

18 种交易类型，每一种操作都有唯一的类型码：

```java
ARRIVAL_IN(1, "收货入库"),       // 货物验收后入账
ARRIVAL_OUT(2, "撤销收货"),      // 撤销入库，冲销
ADJUSTMENT_IN(3, "调整入库"),    // 手工调整增加
ADJUSTMENT_OUT(4, "调整出库"),   // 手工调整减少
PUTAWAY_IN(5, "上架移入"),       // 货物到达目标库位
PUTAWAY_OUT(6, "上架移出"),      // 货物离开收货库位
TRANSFER_IN(7, "移库入"),        // 调拨货物到达目标仓库
TRANSFER_OUT(8, "移库出"),       // 调拨货物离开源仓库
MOVEMENT_IN(9, "移动入"),        // 库内移动到达目标库位
MOVEMENT_OUT(10, "移动出"),      // 库内移动离开源库位
PACKING_IN(11, "拣货移入"),      // 拣货到容器（暂存）
PACKING_OUT(12, "拣货移出"),     // 从库位拣出
SHIPPING_OUT(13, "出库发货"),    // 最终发出
LOSS_OUT(14, "报损出库"),        // 货物报损
SURPLUS_IN(15, "报溢入库"),      // 货物盈余
START_IN(16, "期初导入"),        // 系统初始化库存
JYZXC_OUT(17, "超分配消耗出库"),
JYZXC_IN(18, "超分配消耗入库"),
```

**数据库验证 - 追踪一条库存的完整历史：**
```sql
-- 找一条有流水的库存记录
SELECT id, goods_code, pos_code, inventory_quantity
FROM wms_inventory
WHERE inventory_quantity > 0
LIMIT 5;

-- 查该库存的所有流水（假设 id=1）
SELECT
    trans_type,
    CASE trans_type
        WHEN 1 THEN '收货入库'
        WHEN 2 THEN '撤销收货'
        WHEN 3 THEN '调整入库'
        WHEN 4 THEN '调整出库'
        WHEN 5 THEN '上架移入'
        WHEN 6 THEN '上架移出'
        WHEN 12 THEN '拣货移出'
        WHEN 13 THEN '出库发货'
        ELSE CONCAT('类型', trans_type)
    END AS trans_name,
    trans_quantity,
    trans_doc_no,
    operation_time
FROM wms_inventory_trans_detail
WHERE inventory_id = 1
ORDER BY id;
```

**分析这条库存的"生命历程"：**
- 第一条流水应该是 `trans_type=1`（收货入库），数量为正
- 如果有 `trans_type=5`（上架移入），说明这个记录是上架后的目标库位
- 如果有 `trans_type=12`（拣货移出），说明有出库拣货操作
- 如果有 `trans_type=13`（出库发货），说明货物已经发出

### 第四步：读 `InventoryBaseService`（核心！）（2 小时）

**打开文件：**
```
common-operation/src/main/java/com/hongtu/tz/io/in/service/impl/InventoryBaseService.java
```

这是所有库存操作的"基础类"，入库、出库、移库等所有 Service 都继承它。

**重点方法 1：`createInventory`（新建或追加库存）**

找到这个方法，理解它的逻辑：
```
1. 先查询这个库位+货品+批号的记录是否存在（带 FOR UPDATE 行锁）
2. 如果不存在 → 插入新记录（inventoryQuantity = 传入数量）
3. 如果已存在 → 更新记录（inventoryQuantity += 传入数量）
4. 更新 availableQuantity（可用量同步增加）
```

**重点方法 2：`updateInventoryByLock`（锁定库存）**

找到这个方法：
```java
// 伪代码理解
inventoryQuantity = 不变
availableQuantity = availableQuantity - lockQuantity  // 可用量减少
lockedQuantity = lockedQuantity + lockQuantity         // 锁定量增加
// 写入 wms_inventory_lock 记录
```

**重点方法 3：`updateInventoryByUnlock`（解锁库存）**

```java
// 与锁定相反
availableQuantity = availableQuantity + unlockQuantity  // 可用量增加
lockedQuantity = lockedQuantity - unlockQuantity         // 锁定量减少
// 删除对应的 wms_inventory_lock 记录
```

**重点方法 4：`decrCurrInventory`（扣减库存）**

```java
// 货物实际出库时调用
inventoryQuantity = inventoryQuantity - decrQuantity  // 总量减少
availableQuantity = availableQuantity - decrQuantity  // 可用量减少（锁定量此时已在解锁时处理）
```

**重点方法 5：`createInventoryTransDetail`（写流水）**

找到这个方法，它写入 `wms_inventory_trans_detail` 表：
```java
detail.setInventoryId(inventoryId);
detail.setTransType(transType.getCode());    // 交易类型
detail.setSourceType(sourceType.getCode()); // 来源类型
detail.setTransQuantity(quantity);           // 变动数量
detail.setTransDocNo(docNo);                 // 关联单据号
detail.setOperationTime(operationTime);
```

> 每次操作库存，**必须**同时写流水！不写流水就不知道库存为什么变化了。

### ✅ Day 6 验收

回答以下问题：

**Q1：** 出库单分配了 50 箱库存后，`wms_inventory` 表的哪两个字段变化了，怎么变化？
> 答：`locked_quantity` +50，`available_quantity` -50，`inventory_quantity` 不变

**Q2：** 如果 `available_quantity` 已经是 0，还能分配出库吗？
> 答：不能（除非仓库配置了允许负库存，即 `jyzx.access.minus.inventory=true`）

**Q3：** `ETransType.ARRIVAL_OUT`（code=2，撤销收货）的 `transQuantity` 是正数还是负数？
> 答：负数（减少库存，所以数量为负）

**Q4：** 为什么操作库存时要先 `getInventoryForUpdate`（SELECT FOR UPDATE）？
> 答：防止并发问题——如果两个请求同时修改同一行，没有行锁会导致数量计算错误（超卖/超收）

---

## Day 7：库存服务完整链路与实践

### 第一步：理解 `IInventoryService` 的查询方法（1 小时）

**打开文件：**
```
common-operation/src/main/java/com/hongtu/tz/io/base/service/IInventoryService.java
```

关注这几个重要方法：

**`getListByStrategy`（按分配策略查库存）**：
这是出库分配时调用的核心方法，根据出库单的货品、数量要求，找到可以用的库存行。

**`selectInventoryBatchNos`（查可用批次）**：
PDA 收货时，让用户选择已有批次或创建新批次用。

**`createZeroInventory`（创建零库存占位）**：
超分配场景：库存不够时创建一个数量为 0 的占位记录，后续补货完成后再填数。

### 第二步：读 `InventoryFreezeController`，理解冻结 vs 锁定的区别（30 分钟）

**打开文件：**
```
common-operation/src/main/java/com/hongtu/tz/io/base/controller/InventoryFreezeController.java
```

| 特性 | 锁定（Lock） | 冻结（Freeze） |
|------|-------------|--------------|
| 触发方式 | 系统自动（出库分配、上架等） | 人工手动操作 |
| 持续时间 | 业务完成后自动解除 | 需要人工解冻或审批通过 |
| 用途 | 临时占用库存 | 长期管控（如质量问题、审计等） |
| 可逆性 | 自动撤销 | 需要审批或手动解冻 |

```sql
-- 查看当前冻结单
SELECT freeze_no, freeze_status, freeze_quantity, freeze_reason
FROM wms_inventory_freeze
WHERE freeze_status = 2  -- 2=冻结中
LIMIT 10;
```

### 第三步：查询库存报表，理解结余计算（45 分钟）

**打开文件：**
```
resources/mapper/io/InventoryTransDetailMapper.xml
```

找到 `getInventoryStockList` 这个 SQL（库存结余查询），理解它的计算逻辑：

库存结余不是直接从 `wms_inventory` 查的，而是**通过流水表汇总计算**的：

```sql
-- 简化理解版（实际 SQL 更复杂）
SELECT
    goods_code,
    batch_no,
    SUM(CASE WHEN trans_type IN (1,3,5,9,15,16) THEN trans_quantity ELSE 0 END) AS total_in,   -- 入
    SUM(CASE WHEN trans_type IN (2,4,6,10,13,14) THEN ABS(trans_quantity) ELSE 0 END) AS total_out, -- 出
    SUM(trans_quantity) AS current_stock  -- 当前库存（入-出）
FROM wms_inventory_trans_detail
GROUP BY goods_code, batch_no;
```

> 这种设计的优势：可以查询**任意时间点**的历史库存，而不只是当前库存。

### 第四步：实战 - 追踪一次完整的库存变动（1 小时）

在数据库中找一张已完成的到货通知单，追踪它对库存的影响：

```sql
-- Step 1: 找一张已完成的到货通知单
SELECT id, arrival_no, order_status, order_type
FROM wms_arrival_notice
WHERE order_status = 3   -- 3=收货完成
LIMIT 5;

-- 记下 id（假设是 100）和 arrival_no（假设是 A20260430000001）

-- Step 2: 找对应的收货单
SELECT id, receipt_no FROM wms_receipt
WHERE arrival_notice_id = 100;

-- 记下 receipt_no（假设是 R20260430000001）

-- Step 3: 查这次收货产生的库存记录
SELECT id, goods_code, pos_code, batch_no,
       inventory_quantity, available_quantity, locked_quantity
FROM wms_inventory
WHERE id IN (
    SELECT inventory_id FROM wms_inventory_trans_detail
    WHERE trans_doc_no = 'R20260430000001'
      AND trans_type = 1   -- 收货入库
);

-- Step 4: 查这次收货的完整流水（按时间顺序）
SELECT trans_type, trans_quantity, trans_doc_no, operation_time,
       CASE trans_type
           WHEN 1 THEN '收货入库'
           WHEN 5 THEN '上架移入'
           WHEN 6 THEN '上架移出'
           WHEN 13 THEN '出库发货'
           ELSE CONCAT('type=', trans_type)
       END as type_name
FROM wms_inventory_trans_detail
WHERE trans_doc_no LIKE 'R20260430000001%'
   OR trans_doc_no LIKE 'PA%'  -- 上架单也可能关联
ORDER BY operation_time, id;
```

**分析你找到的数据，回答：**
1. 这批货最终落在哪个库位？
2. 收货入库时的数量是多少？
3. 如果有上架流水，说明这批货经历了什么过程？

### 第五步：读 `InventoryController` 接口（30 分钟）

**打开文件：**
```
common-operation/src/main/java/com/hongtu/tz/io/base/controller/InventoryController.java
```

重点看这几个接口：
- `list`：库存明细列表（前端的库存查询页）
- `importData`：期初导入（系统初始化时批量导入历史库存）
- `editCostUnitPrice`：修改成本单价（财务用）

**用 Postman 测试库存明细接口：**
```http
GET http://localhost:端口/inventory/inventory/list?pageNum=1&pageSize=10
Authorization: Bearer {你的token}
```

观察返回数据中的 `inventoryQuantity`、`availableQuantity`、`lockedQuantity` 字段，对应数据库中你之前查到的数据。

### ✅ Day 7 验收

**核心自测题（不看任何文档，30 分钟内完成）：**

**题目 1（画图题）**：画出下面场景中 `wms_inventory` 记录的数量变化：

```
货品 G001 在库位 A-01-01，初始：inventoryQty=0, availableQty=0, lockedQty=0

操作 1：收货入库 100 箱
操作 2：出库单分配 30 箱（出库锁定）
操作 3：PDA 拣货 30 箱（从 A-01-01 移到虚拟容器位置）
操作 4：发货完成（货物实际发出）

请写出每步操作后三个字段的值。
```

<details>
<summary>参考答案（思考后再看）</summary>

操作 1 后：inventoryQty=100, availableQty=100, lockedQty=0
操作 2 后：inventoryQty=100, availableQty=70, lockedQty=30
操作 3 后：inventoryQty=70, availableQty=70, lockedQty=0（A-01-01 的记录；拣货时解锁+扣减，新建虚拟位记录）
操作 4 后：A-01-01 的记录已经是 70；虚拟位的 30 箱被扣减掉

</details>

**题目 2（查数据库）**：在 `wms_inventory_trans_detail` 中，找一条 `trans_type=13`（出库发货）的记录，通过 `trans_doc_no` 关联到 `wms_outbound_order`，确认是哪个出库单的货发出去了。

**题目 3（说原理）**：为什么 `wms_inventory` 表中有时候会有 `inventory_quantity=0` 的记录不被删除？
> 答：为了保留历史记录和关联关系，删了流水就断了，系统用"逻辑零"代替物理删除。

---

## 第二阶段总结

学完这两天，你应该能：

1. **看到任何操作，立刻知道** `wms_inventory` 的哪几个字段会怎么变
2. **看到 `ETransType` 的数字**，知道它对应什么业务场景
3. **理解为什么库存操作需要行锁**（SELECT FOR UPDATE）
4. **在数据库里追踪任意一笔库存变动**的完整历史

> 下一步（第三阶段）：带着这些库存知识，去看入库模块是如何调用 `inventoryInService` 来改变库存的。