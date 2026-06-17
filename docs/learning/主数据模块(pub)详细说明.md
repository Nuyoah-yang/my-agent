# 主数据模块（pub）详细说明文档

> 面向后端实习生 · 宏图智能物流 WMS
> 模块路径：`common-operation/src/main/java/com/hongtu/tz/pub/`

---

## 一、模块概述

主数据模块（pub）是 WMS 系统运转的"基础字典"，提供所有其他业务模块依赖的基础数据：仓库、库区、库位、货主、承运商、货品等。同时，`CommonController` 是前端下拉框数据的统一出口，也负责各类单据编号的生成。

### 核心职责

1. **主数据 CRUD**：仓库、库区、库位、货主、承运商、货品及分类的增删改查
2. **前端下拉数据**：通过 `CommonController` 提供所有下拉选项
3. **单号生成**：通过 `SerialNumberManager` 统一生成各类单据编号
4. **ERP 基础数据读取**：通过 `ErpBaseService` 读取 ERP 系统的保管账、客户、供应商等数据

---

## 二、代码目录结构

```
com.hongtu.tz.pub/
├── controller/                         # REST 接口层
│   ├── CommonController                    # 公共下拉 + 单号生成（最常用！）
│   ├── PubStoreController                  # 仓库管理
│   ├── PubStoreAreaController              # 库区管理
│   ├── PubPosController                    # 库位管理
│   ├── PubCargoOwnerController             # 货主管理
│   ├── PubCarrierController                # 承运商管理
│   ├── GoodsController                     # 货品管理
│   ├── GoodsCategoryController             # 货品分类管理
│   ├── GoodsPackController                 # 包装规格管理
│   └── HistoryReportController             # 历史报告上传（质检报告等）
│
├── domain/                             # 数据库实体 PO
│   ├── PubStore                            # 仓库
│   ├── PubStoreArea                        # 库区
│   ├── PubPos                              # 库位
│   ├── PubCargoOwner                       # 货主
│   ├── PubCargoOwnerRel                    # 货主与ERP客户映射
│   ├── PubStoreRel                         # 仓库与ERP保管账映射
│   ├── PubCarrier                          # 承运商
│   ├── PubRegion                           # 地区（省市区）
│   ├── GoodsPO                             # 货品
│   ├── GoodsCategoryPO                     # 货品分类
│   ├── GoodsPackPO                         # 包装规格
│   ├── GoodsCategoryAreaRelPO              # 货品分类-库区关联
│   ├── GoodsInventoryCapacityPO            # 库容配置
│   └── HistoryReportPO                     # 历史质检报告
│
├── entity/                             # 额外 DTO
│   ├── PdaGoods                            # PDA 货品对象
│   └── GoodsCheckDTO                       # 货品校验 DTO
│
├── enums/                              # 枚举常量
│   ├── EStoreType                          # 仓库类型
│   ├── EAreaType                           # 库区类型
│   ├── EPosType                            # 库位类型
│   └── EWholePartType                      # 整散标识
│
├── erp/                                # ERP 基础数据读取
│   ├── ErpBaseService.java                 # ERP 查询服务（只读）
│   ├── entity/                             # ERP 查询结果对象
│   │   ├── BaseStorage                     # ERP 保管账
│   │   ├── BaseCustomer                    # ERP 客户
│   │   ├── BaseSupplier                    # ERP 供应商
│   │   └── BaseGoodsTransRate              # ERP 货品转换率
│   └── mapper/ErpBaseMapper.java           # ERP 数据 Mapper
│
├── mapper/                             # WMS 主数据 Mapper
└── service/
    ├── *Service.java                   # 业务接口
    └── impl/                           # 业务实现
```

---

## 三、数据库表清单

| 数据库表 | 对应实体 | 说明 |
|---------|---------|------|
| `pub_store` | `PubStore` | 仓库主表 |
| `pub_store_area` | `PubStoreArea` | 库区主表 |
| `pub_pos` | `PubPos` | 库位主表 |
| `pub_cargo_owner` | `PubCargoOwner` | 货主主表 |
| `pub_cargo_owner_rel` | `PubCargoOwnerRel` | 货主与ERP客户映射 |
| `pub_store_rel` | `PubStoreRel` | 仓库与ERP保管账映射（重要！） |
| `pub_carrier` | `PubCarrier` | 承运商 |
| `pub_region` | `PubRegion` | 省市区数据 |
| `pub_goods` | `GoodsPO` | 货品主表 |
| `pub_goods_category` | `GoodsCategoryPO` | 货品分类 |
| `pub_goods_pack` | `GoodsPackPO` | 包装规格 |
| `pub_goods_category_area_rel` | `GoodsCategoryAreaRelPO` | 货品分类-库区关联 |
| `pub_goods_inventory_capacity` | `GoodsInventoryCapacityPO` | 库容配置 |
| `pub_history_report` | `HistoryReportPO` | 历史质检报告 |

**ERP 侧表（只读，不在 WMS 数据库中维护）：**

| ERP 表 | 实体 | 说明 |
|--------|------|------|
| `erp.base_storage` | `BaseStorage` | ERP 保管账（对应 WMS 保管账维度） |
| `erp.base_customer` | `BaseCustomer` | ERP 客户 |
| `erp.base_supplier` | `BaseSupplier` | ERP 供应商 |
| `erp.base_goods_trans_rate` | `BaseGoodsTransRate` | 货品转换率 |

---

## 四、关键枚举详解

### 4.1 仓库类型（`EStoreType`）

| 值 | 含义 |
|----|------|
| 1 | 原辅包仓库 |
| 2 | 成品仓库 |

### 4.2 库区类型（`EAreaType`）

| 值 | 含义 | 说明 |
|----|------|------|
| 1 | 收货区 | 货物到达后的暂存区 |
| 2 | 待检区 | 等待质检的区域 |
| 3 | 存储区 | 正式存放货物的区域 |
| 4 | 发货区 | 出库前的集货区 |
| 5 | 集货区 | 订单集货暂存区 |
| 6 | 异常区 | 不合格/异常货物隔离区 |
| 7 | 分拣区 | 拣货操作区 |
| 8 | 虚拟区 | 系统虚拟区（如在途库存）|

### 4.3 库位类型（`EPosType`）

| 值 | 含义 | 对应库区类型 |
|----|------|------------|
| 1 | 收货库位 | 收货区 |
| 2 | 存储库位 | 存储区 |
| 3 | 质检库位 | 待检区 |
| 4 | 发货库位 | 发货区 |
| 5 | 集货库位 | 集货区 |
| 6 | 虚拟库位 | 虚拟区（如拣货容器位置）|
| 7 | 异常库位 | 异常区 |

### 4.4 整散标识（`EWholePartType`）

| 值 | 含义 |
|----|------|
| 0 | 不区分（混存） |
| 1 | 整件库位 |
| 2 | 散件库位 |

---

## 五、核心实体字段详解

### 5.1 仓库（`PubStore`）

```
storeCode / storeName   仓库编码 / 名称
storeType               仓库类型（EStoreType：1原辅包 2成品）
storeArea               仓库面积(m²)
contactPerson           联系人
contactPhone            联系电话
region / address        省市区 / 详细地址
status                  启用状态
rels                    与 ERP 保管账的映射关系（pub_store_rel）
```

### 5.2 库区（`PubStoreArea`）

```
storeCode               所属仓库编码
areaCode / areaName     库区编码 / 名称
areaType                库区类型（EAreaType：1收货区~8虚拟区）
shelfType               货架类型
  1 = 立体货架（多层货架）
  2 = 平面货架（单层货架）
  3 = 平面地堆（直接堆放地面）
storageCondition        存储条件
  1 = 常温
  2 = 阴凉
  3 = 冷藏
status                  是否启用
```

### 5.3 库位（`PubPos`）——字段最多，最重要！

```
storeCode / areaCode    所属仓库、库区编码
posCode / posName       库位编码 / 名称
posType                 库位类型（EPosType：1收货~7异常）
abcClass                ABC 分类（A/B/C 表示频率高/中/低）

物理属性：
  weightLimit           承重上限(KG)
  posLength             库位长度(cm)
  posWidth              库位宽度(cm)
  posHeight             库位高度(cm)
  rowNum                行号
  columnNum             列号
  levelNum              层号
  stackLimit            最大堆叠层数

动线序号（用于优化拣货路径）：
  putawaySequence       上架序号（上架时按此顺序）
  pickingSequence       拣货序号（拣货时按此顺序）
  checkSequence         盘点序号（盘点时按此顺序）

混存规则：
  allowGoodsMixed       是否允许混放不同货品（true=允许）
  allowBatchMixed       是否允许混放不同批次（true=允许）
  wholePartTag          整散标识（0不区分/1整件/2散件）
  categoryCodeLimit     可存货品种类数量上限

保管账绑定：
  storageCodes          绑定的 ERP 保管账编码（多个逗号分隔）
  storageNames          保管账名称（展示用）

status                  是否启用
```

> **重要**：`putawaySequence`、`pickingSequence` 直接影响系统推荐的上架库位和拣货路径，是库位配置中最关键的参数。

### 5.4 货主（`PubCargoOwner`）

```
ownerCode / ownerName   货主编码 / 名称
contact / contactPhone  联系人 / 电话
region / address        省市区 / 详细地址
status                  启用
rels                    与 ERP 客户的关联关系（pub_cargo_owner_rel）
```

### 5.5 货品（`GoodsPO`）——重要字段

```
ownerCode / ownerName   货主编码 / 名称（属于哪个货主的货品）
goodsCode / goodsName   货品编码 / 名称
goodsDesc               货品描述
specification           规格（如 500g/袋）
originPlace             产地
grade                   等级
unit                    计量单位（基本单位，如 g、ml、粒）

分类：
  categoryCode          货品分类编码（二级）
  categoryName          分类名称

养护管理：
  manageMaintenance     是否需要养护（true=是）
  maintenanceType       养护类型
  maintenanceCycle      养护周期(天)

效期管理：
  manageExpire          是否管理效期（true=是）
  expireDays            效期天数（入库后多少天过期）
  standardAgeDays       标准库龄(天)

存储要求：
  storageCondition      存储条件（同库区）

其他：
  imageUrl              货品图片
  status                是否启用
  inventoryFrequency    库存频度（影响ABC分类）
  upstreamGoodsCode     上游货品编码（ERP侧编码）

非表字段（查询/展示用）：
  packList              包装规格列表（对应 pub_goods_pack）
  capacityList          库容配置列表
  errorMsg              导入时的错误信息
  packSpecification     包装规格描述（展示用）
  caseQuantity          箱规数量
  caseUnit              箱规单位
```

### 5.6 货品分类（`GoodsCategoryPO`）

```
categoryCode / categoryName   分类编码 / 名称
parentCode / parentName       上级分类（支持两级分类）
remark                        备注
areaRelList                   关联的存储库区（pub_goods_category_area_rel）
                              → 用于上架策略推荐库区
```

---

## 六、API 接口详解

### 6.1 公共下拉接口（`/pub/common`）——最常用！

前端几乎所有下拉选择器都通过这里获取数据，不需要权限控制。

| HTTP | 路径 | 参数 | 返回值 | 说明 |
|------|------|------|--------|------|
| GET | `/generateBillNo` | `billType`（Integer） | 单据编号字符串 | 生成各类单据编号 |
| GET | `/cargoOwnerList` | — | `[{value:编码, label:名称}]` | 货主下拉列表 |
| GET | `/carrierList` | — | 同上 | 承运商下拉列表 |
| GET | `/storeList` | — | 同上 | 仓库下拉列表（按用户权限过滤） |
| GET | `/areaList` | `storeCode`（可选） | 同上 | 库区下拉列表 |
| GET | `/posList` | `storeCode`, `areaCode` | 同上 | 库位下拉列表 |
| GET | `/storageList` | `storeCode`, `all`（Boolean） | 同上 | ERP 保管账下拉列表 |
| GET | `/customerList` | `customerCode`, `customerName` | 分页列表 | ERP 客户下拉（查 ERP 表） |
| GET | `/supplierList` | `supplierCode`, `supplierName` | 分页列表 | ERP 供应商下拉（查 ERP 表） |
| GET | `/goodsCategoryList` | — | 分类列表 | 货品分类列表 |
| GET | `/goodsList` | — | 货品列表 | 所有货品 |
| GET | `/getDocTypeList` | — | 单据类型列表 | 单据类型枚举 |
| GET | `/getCurrentTime` | — | 当前时间 | 匿名接口，获取服务器时间 |

> **注意**：`storageList` 接口中，`all=false`（默认）时会按仓库权限过滤可用的保管账；`all=true` 时返回全部保管账（仅用于维护仓库-保管账映射关系时）。

---

### 6.2 仓库管理（`/pub/store`）

| HTTP | 路径 | 权限标识 | 说明 |
|------|------|---------|------|
| GET | `/list` | `pub:store:list` | 分页列表 |
| POST | `/export` | `pub:store:export` | 导出 |
| GET | `/{id}` | `pub:store:query` | 详情 |
| POST | `/` | `pub:store:add` | 新增 |
| PUT | `/` | `pub:store:edit` | 编辑 |
| DELETE | `/{ids}` | `pub:store:remove` | 删除 |

---

### 6.3 库区管理（`/pub/area`）

| HTTP | 路径 | 权限标识 | 说明 |
|------|------|---------|------|
| GET | `/list` | `pub:area:list` | 分页列表 |
| POST | `/export` | `pub:area:export` | 导出 |
| GET | `/{id}` | `pub:area:query` | 详情 |
| POST | `/` | `pub:area:add` | 新增 |
| PUT | `/` | `pub:area:edit` | 编辑 |
| DELETE | `/{ids}` | `pub:area:remove` | 删除 |
| GET | `/effectYes/{ids}` | `pub:area:effectyes` | **启用** |
| GET | `/effectNo/{ids}` | `pub:area:effectno` | **禁用** |
| POST | `/importData` | `pub:area:importdata` | Excel 导入 |
| POST | `/importTemplate` | — | 下载导入模板 |

---

### 6.4 库位管理（`/pub/pos`）

| HTTP | 路径 | 权限标识 | 说明 |
|------|------|---------|------|
| GET | `/list` | `pub:pos:list` | 分页列表 |
| POST | `/export` | `pub:pos:export` | 导出 |
| GET | `/{id}` | `pub:pos:query` | 详情 |
| POST | `/` | `pub:pos:add` | 新增 |
| PUT | `/` | `pub:pos:edit` | 编辑 |
| DELETE | `/{ids}` | `pub:pos:remove` | 删除 |
| GET | `/effectYes/{ids}` | `pub:pos:effectyes` | 启用 |
| GET | `/effectNo/{ids}` | `pub:pos:effectno` | 禁用 |
| POST | `/importData` | `pub:pos:importdata` | Excel 批量导入库位 |
| POST | `/importTemplate` | — | 下载导入模板 |

---

### 6.5 货主管理（`/pub/owner`）

| HTTP | 路径 | 权限标识 | 说明 |
|------|------|---------|------|
| GET | `/list` | `pub:owner:list` | 分页列表 |
| POST | `/export` | `pub:owner:export` | 导出 |
| GET | `/{id}` | `pub:owner:query` | 详情 |
| POST | `/` | `pub:owner:add` | 新增 |
| PUT | `/` | `pub:owner:edit` | 编辑 |
| DELETE | `/{ids}` | `pub:owner:remove` | 删除 |

---

### 6.6 承运商管理（`/pub/carrier`）

| HTTP | 路径 | 权限标识 | 说明 |
|------|------|---------|------|
| GET | `/list` | `pub:carrier:list` | 分页列表 |
| GET | `/{id}` | `pub:carrier:query` | 详情 |
| POST | `/` | `pub:carrier:add` | 新增 |
| PUT | `/` | `pub:carrier:edit` | 编辑 |
| DELETE | `/{ids}` | `pub:carrier:remove` | 删除 |
| POST | `/importData` | `pub:carrier:importdata` | Excel 导入 |

---

### 6.7 货品管理（`/wms/goods`）

| HTTP | 路径 | 说明 |
|------|------|------|
| GET | `/getPageList` | 分页查询 |
| POST | `/add` | 新增货品 |
| POST | `/edit` | 编辑货品 |
| GET | `/batchDelete/{ids}` | 批量删除 |
| GET | `/getInfo/{id}` | 货品详情 |
| POST | `/export` | 导出 |
| POST | `/importData` | Excel 批量导入 |
| POST | `/downloadErrorData` | 导出导入失败数据 |
| POST | `/importTemplate` | 下载导入模板 |
| POST | `/enable/{ids}` | 启用 |
| POST | `/disable/{ids}` | 禁用 |

---

### 6.8 货品分类（`/wms/goodsCategory`）

| HTTP | 路径 | 说明 |
|------|------|------|
| GET | `/getPageList` | 分页查询 |
| POST | `/add` | 新增分类 |
| POST | `/update` | 编辑 |
| GET | `/batchDelete/{ids}` | 批量删除 |
| GET | `/getInfo/{id}` | 分类详情（含关联库区） |

---

### 6.9 包装规格（`/wms/goodsPack`）

| HTTP | 路径 | 说明 |
|------|------|------|
| GET | `/getPageList` | 分页查询 |
| POST | `/add` | 新增 |
| POST | `/edit` | 编辑 |
| GET | `/batchDelete/{ids}` | 批量删除 |
| GET | `/getInfo/{id}` | 详情 |
| POST | `/export` | 导出 |
| POST | `/importData` | Excel 导入 |

---

## 七、单号生成机制

### 7.1 原理

所有 WMS 单据编号都通过 `SerialNumberManager` 统一生成，底层使用 **Redis 原子自增** 保证唯一性。

### 7.2 生成规则

```
单号 = 业务前缀(bizCode) + 日期(yyyyMMdd) + 6位序号

例：
  到货通知单：A20260430000001
  收货单：    R20260430000001
  出库单：    ??20260430000001
```

**Redis Key 按日历日滚动**：每天从 `000001` 重新开始，`EXPIRE 86400` 秒自动失效。

### 7.3 常见单据类型（`EBillType`）

| 单据类型 | bizCode | 示例单号前缀 |
|---------|---------|------------|
| 到货通知单(ARIVAL_NOTICE) | A | A20260430... |
| 收货单(RECEIPT) | R | R20260430... |
| 上架单(PUTAWAY) | P | P20260430... |
| 拣货单(PICKING_ORDER) | PO | PO20260430... |
| 拣货明细(PICKING_DETAIL) | PDL | PDL20260430... |
| 任务(TASK) | T | T20260430... |
| 退货单(GOODS_RETURN) | RE | RE20260430... |
| 处方(PRESCRIPTION) | CF | CF20260430... |

### 7.4 使用方式

```java
// 通过 CommonController 生成单号（前端调用）
GET /pub/common/generateBillNo?billType=1  // 生成到货通知单号

// 后端直接调用
String billNo = serialNumberManager.generateBillNo(EBillType.ARIVAL_NOTICE);
// 或携带业务日期
String billNo = serialNumberManager.generateBillNo(EBillType.RECEIPT, new Date());
```

---

## 八、ERP 数据读取

### 8.1 `ErpBaseService` 说明

WMS 和 ERP 共享同一个数据库（或同一个 Schema），通过 `ErpBaseMapper` **直接查询 ERP 数据库表**（不通过 HTTP API 调用）。

```
WMS 数据库             ERP 数据库（同一DB的不同Schema）
pub_store_rel    →    erp.base_storage（保管账）
pub_cargo_owner_rel → erp.base_customer（客户）
                      erp.base_supplier（供应商）
                      erp.base_goods_trans_rate（货品转换率）
```

### 8.2 关键映射关系

**仓库-保管账映射（`pub_store_rel`）**：
- WMS 的"仓库"对应 ERP 的"保管账"（`storageCode`）
- 一个 WMS 仓库可以对应多个 ERP 保管账
- 出库单创建时，通过保管账 + `PubStoreRel` 找到对应的 WMS 仓库

**货主-客户映射（`pub_cargo_owner_rel`）**：
- WMS 的"货主"对应 ERP 的"客户"
- 用于在 ERP 下推出库单时，将客户编码转换为 WMS 货主编码

---

## 九、数据关联关系图

```
ERP 保管账(storageCode) ←→ pub_store_rel ←→ WMS 仓库(pub_store)
                                                    ↓
ERP 客户(customerCode) ←→ pub_cargo_owner_rel ←→ 货主(pub_cargo_owner)
                                                    ↓
                                              货品(pub_goods)
                                                    ↓
                                        货品分类(pub_goods_category)
                                                    ↓
                              货品分类-库区关联(pub_goods_category_area_rel)
                                                    ↓
                                            库区(pub_store_area)
                                                    ↓
                                            库位(pub_pos)
```

**读懂这张图**：从 ERP 下推一个出库单，系统首先通过保管账找到仓库，通过客户找到货主，然后按货品的分类找到应该存放的库区，再找到具体库位。

---

## 十、常见问题 Q&A

| 问题 | 解答 |
|------|------|
| 库区类型和库位类型有什么关系？ | 不是强制对应的，但一般收货区里用收货库位、存储区里用存储库位等，系统通过枚举值来区分用途 |
| 什么是保管账？ | ERP 系统的概念，类似于"库存归属的账户"，WMS 通过 `pub_store_rel` 将仓库与保管账绑定 |
| 货品分类和库区关联有什么用？ | 上架策略中"按分类库区"规则依赖此关联：系统根据货品分类自动推荐应该存放到哪个库区 |
| 为什么有些接口没有 `@PreAuthorize`？ | 货品/货品分类接口未加细粒度权限，可能是全局安全配置已处理，或这些接口对所有登录用户开放 |
| 单号为什么用 Redis 而不是数据库序列？ | Redis 原子操作性能更高，可以支持高并发的单号生成，避免数据库锁竞争 |
| 动线序号（putawaySequence/pickingSequence）怎么配置？ | 在新增库位时手工填写，数值越小越优先；系统在推荐上架库位和拣货路径时按此排序，目的是减少人员行走路线，提升效率 |

---

## 十一、读代码建议

### 优先阅读顺序

1. **`PubPos` 实体** → 库位是最复杂的主数据，字段最多，值得仔细了解
2. **`CommonController`** → 理解前端下拉数据的来源
3. **`SerialNumberManager`** → 理解单号生成的实现原理（在 `io/base/common/serialnumber/` 目录下）
4. **`ErpBaseService`** → 理解 WMS 和 ERP 的数据打通方式
5. **`pub_store_rel` 相关逻辑** → 理解仓库-保管账映射（对理解整个系统很关键）

### 一句话总结

> 主数据是地基：没有正确配置仓库、库区、库位、货品，所有的入库、出库、盘点都无法正常运行。

---

*文档生成时间：2026-04-30*
*基于 common-operation 主数据模块（com.hongtu.tz.pub）源码分析*