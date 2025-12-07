# 项目：医院预约系统 (HABMS)

本项目是一个演示/教学用的医院预约管理系统（Hospital Appointment Booking Micro System，HABMS）。采用 C/S 架构：后端为 Java 服务（嵌入式 Derby 数据库），前端为基于 Swing 的 Java 客户端。

本文档面向开发者，涵盖架构、主要接口（JSON/TCP）、模块职责、构建/运行及调试指南，便于在此基础上扩展或迁移到生产环境。

## 运行环境 & 依赖

- Java 11
- Maven
- 依赖（见 `pom.xml`）：`org.apache.derby:derby`, `com.fasterxml.jackson.core:jackson-databind`, `org.apache.poi:poi`/`poi-ooxml`, `org.apache.pdfbox:pdfbox`

## 重要源码位置

- `src/main/java/com/habms/server`
  - `Server.java`：程序入口，负责 `Database.init()` 与监听 TCP 端口（默认为 `9090`）。
  - `ServerHandler.java`：每个客户端连接对应实例，负责解析 JSON、路由 action、调用 `Database` 并返回 JSON 响应。
  - `Database.java`：封装所有 JDBC 操作、建表与业务逻辑（导入/导出/统计等）。
- `src/main/java/com/habms/client`
  - `ClientApp.java`：Swing 客户端主程序，提供 UI 并通过 JSON/TCP 向服务器发起请求。
  - `FormDialog.java`, `FormFactory.java`：表单/对话框辅助类。
- `src/main/java/com/habms/tools`
  - `SampleDataGenerator.java`：用于生成示例 `sample.xlsx`（导入测试）。
- `tools/run_integration_test.ps1`：PowerShell 脚本，示例自动化测试（构建、启动、导入、导出、生成 PDF、停止）。

## 架构要点

- 协议：简单的一行 JSON 请求 / 一行 JSON 响应（文本协议），便于用流式 API 处理。
- 会话：`ServerHandler` 将登录用户与角色保存在实例字段中，意味着会话绑定到 TCP 连接；如果多个短连接会话不会自动共享。
- 文件传输：Excel/PDF 等二进制数据通过 Base64 字符串嵌入 JSON 字段 `content` 传输。

## JSON API（主要 action 摘要）

注：所有响应至少包含 `status`（`OK` 或 `ERR`）。出错时包含 `message`。

- `login`：请求 `{"action":"login","username":"...","password":"..."}` → 成功 `{"status":"OK","role":"ADMIN|PATIENT"}`。
- `logout`：`{"action":"logout"}` → `{"status":"OK"}`。
- `register`：`{"action":"register","username":"...","password":"...","fullname":"...","idcard":"...","phone":"..."}`。
- `list_doctors`：`{"action":"list_doctors"}` → `{"status":"OK","data":[{"id":"1","name":"...","dept":"...","info":"..."}, ...]}`。
- `search_name`：`{"action":"search_name","q":"关键字"}` → `{"status":"OK","data":[{id,name,dept,info},...]}`（已改为结构化返回）。
- `search_dept`：`{"action":"search_dept","q":"关键字"}` → 结构化 `data` 返回。
- `list_appts`：`{"action":"list_appts","doctorId":123}` → 返回 `data` 列表。
- `book`：`{"action":"book","doctorId":1,"patientName":"张三","time":"yyyy-MM-dd'T'HH:mm"}`（需登录）。
- `cancel`：`{"action":"cancel","apptId":123"}`。
- 管理员操作（需 ADMIN）：`add_doctor`, `update_doctor`, `add_schedule`, `update_schedule`。
- 文件/报表：
  - `import_doctors_xls`：`{"action":"import_doctors_xls","content":"<base64-xlsx>"}` → `{"status":"OK","addedDoctors":N,"addedSchedules":M}`。
  - `export_appointments_xls`：`{"action":"export_appointments_xls"}` → `{"status":"OK","filename":"appointments.xlsx","content":"<base64-xlsx>"}`。
  - `generate_report_pdf`：`{"action":"generate_report_pdf"}` → `{"status":"OK","filename":"report.pdf","content":"<base64-pdf>"}`。

## 模块职责与开发指南

- `Server`：保持简单启动逻辑。若要改变端口或增加启动参数，修改 `Server.java` 并在 `main` 中解析参数。
- `ServerHandler`：所有 action 的路由位置。新增 API 时在 `handleJson` 的 `switch` 中添加分支，并使用 `mapper.writeValueAsString(resp)` 返回结果。
- `Database`：封装所有 SQL。若要替换为网络 DB（Postgres/MySQL），修改 `DB_URL` 和 SQL 兼容性并测试事务边界。
- `ClientApp`：当前以 GUI 为主的演示客户端；测试脚本使用低层 TCP 连接直接发送 JSON，用于保持会话（同一连接）。

## 构建与运行（示例命令，PowerShell）

```pwsh
# 1) 拉取依赖并构建
mvn dependency:copy-dependencies -DoutputDirectory=target/dependency
mvn -DskipTests package

# 2) 启动服务器（占用终端）
java -cp "target/classes;target/dependency/*" com.habms.server.Server

# 3) 启动客户端（另开终端）
java -cp "target/classes;target/dependency/*" com.habms.client.ClientApp
```

集成测试（示例）：

```pwsh
tools\run_integration_test.ps1
```

该脚本示范如何在单一 TCP 连接中完成登录、导入 `sample.xlsx`、导出 `appointments.xlsx`、生成 `report.pdf`，然后停止服务器。

## 常见问题与建议

- NoClassDefFoundError：运行时请确保类路径包含 `target/dependency/*`，或者使用 `mvn dependency:copy-dependencies`。
- 会话管理：当前会话绑定到连接，若需要无状态/跨连接会话，请改造为基于 token/JWT 的鉴权。
- PDF 中文支持：在生产部署中请指定可用的 CJK 字体路径并在 `ServerHandler` 中使用 `PDType0Font.load()` 加载。
- 导入校验：建议增强 Excel 导入的逐行校验并返回详细错误列表以便用户修正。
