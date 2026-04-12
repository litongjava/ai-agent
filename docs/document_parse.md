# 文档解析异步接口说明

## 一、概述

该服务提供异步文档解析能力，整体流程为：

1. 客户端通过接口 `POST /api/v1/async/documents/parse` 上传文件，创建解析任务。
2. 接口立即返回任务 ID。
3. 客户端根据返回的任务 ID，轮询接口 `/api/v1/task` 查询任务解析结果。

---

## 二、创建文档解析任务

### 请求

* **URL**

  `POST http://127.0.0.1/api/v1/async/documents/parse`

* **Method**

  `POST`

* **Content-Type**

  `multipart/form-data`

* **请求参数**

  | 参数名  | 类型     | 位置   | 必填 | 说明     |
  | ---- | ------ | ---- | -- | ------ |
  | file | binary | form | 是  | 待解析的文件 |

### 请求示例

```bash
curl -X POST "http://127.0.0.1/api/v1/async/documents/parse" \
  -F "file=@/path/to/your/file.pdf"
```

### 响应

```json
{
    "data": {
        "id": "580332660355862528",
        "result": null
    },
    "code": 1,
    "msg": null,
    "ok": true,
    "error": null
}
```

### 字段说明

* `data.id`：字符串，任务 ID，用于后续查询解析结果。
* `data.result`：固定为 `null`，创建任务时不返回解析结果。
* `code`：业务状态码，示例中为 `1` 表示成功（可根据实际业务约定补充说明）。
* `msg`：业务提示信息，成功时可为 `null` 或空。
* `ok`：布尔类型，请求是否成功，`true` 表示成功。
* `error`：错误信息对象，成功时为 `null`。

---

## 三、查询解析任务结果

### 请求

* **URL**

  `http://127.0.0.1/api/v1/task`

* **Method**

  一般为 `GET`（如果你后端用的是 POST，这里可以改成 POST）

* **请求参数**

  | 参数名 | 类型     | 位置    | 必填 | 说明            |
  | --- | ------ | ----- | -- | ------------- |
  | id  | string | query | 是  | 创建任务时返回的任务 ID |

### 请求示例

```bash
curl "http://127.0.0.1/api/v1/task?id=580332660355862528"
```

### 响应示例（任务已完成）

```json
{
    "data": {
        "id": null,
        "result": "fdf"
    },
    "code": 1,
    "msg": null,
    "ok": true,
    "error": null
}
```

### 字段说明

* `data.id`
  示例中为 `null`。

  * 若你实际实现中在任务完成时仍返回任务 ID，可以调整为返回任务 ID，并在文档中说明。
  * 当前示例表示：任务完成后，主要通过 `result` 字段获取结果。

* `data.result`
  解析结果内容，例如：

  * 提取后的文本
  * 结构化数据
  * 或你业务定义的其他结果格式

* `code`：业务状态码，`1` 表示成功。

* `msg`：业务提示信息。

* `ok`：请求是否成功，`true` 表示成功。

* `error`：错误信息对象，成功时为 `null`。

> 如果有“解析中”“失败”等状态，你可以在 `code`、`msg`、`error` 中做进一步约定，并在文档中补充一张“错误码/状态码”表。

---

## 四、典型调用流程示例

1. **上传文件并创建任务**

   ```bash
   curl -X POST "http://127.0.0.1/api/v1/async/documents/parse" \
     -F "file=@/path/to/your/file.pdf"
   ```

   得到返回：

   ```json
   {
       "data": {
           "id": "580332660355862528",
           "result": null
       },
       "code": 1,
       "msg": null,
       "ok": true,
       "error": null
   }
   ```

2. **轮询任务结果**

   客户端拿到 `id` 后，定期调用：

   ```bash
   curl "http://127.0.0.1/api/v1/task?id=580332660355862528"
   ```

   * 若任务未完成：可以约定返回 `result = null` 或特定错误码/状态码。
   * 若任务完成：例如返回

     ```json
     {
         "data": {
             "id": null,
             "result": "fdf"
         },
         "code": 1,
         "msg": null,
         "ok": true,
         "error": null
     }
     ```
