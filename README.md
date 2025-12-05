# 项目：医院预约系统 (HABMS)

说明：

- 架构：C/S（服务器 + Swing 客户端）
- 构建工具：Maven
- 数据库：JavaDB (Apache Derby) 嵌入式

## 更新

1. 接口改为了json作为载体
2. 添加了角色概念，以及对应的权限控制
3. 可以注册、登录、退出账户
4. 关闭逻辑优化：客户端关闭会通知服务器，服务器关闭（crtl+c）会关闭数据库

## 快速运行

1. 打开命令行到项目根目录：

   pwsh
   cd e:\Xprogram\java\project2-HABMS

2. 启动服务器：

    ```pwsh
    cd e:\Xprogram\java\project2-HABMS
    mvn -DskipTests package dependency:copy-dependencies
    java -cp "target/classes;target/dependency/*" com.habms.server.Server
    ```

3. 启动客户端（另开一个终端）：

    ```pwsh
    cd e:\Xprogram\java\project2-HABMS
    java -cp "target/classes;target/dependency/*" com.habms.client.ClientApp
    ```

4. 直接测试页面

    ```pwsh
    $client = New-Object System.Net.Sockets.TcpClient('127.0.0.1',9090)
    $stream = $client.GetStream()
    $writer = New-Object System.IO.StreamWriter($stream); $writer.AutoFlush = $true
    $writer.WriteLine('LOGIN|admin|admin')
    $reader = New-Object System.IO.StreamReader($stream)
    while(($line=$reader.ReadLine()) -ne '<<END>>'){ Write-Output $line }
    $client.Close()
    ```

说明：首次启动服务器会自动创建嵌入式 Derby 数据库并插入演示用户和医生数据。默认演示用户：`admin/admin`。

新增功能说明：

- 管理员已内置，用户名/密码：`admin`/`admin`。
- 患者可以注册（`REGISTER`）、登录、注销（`DELETE_ACCOUNT`）、修改个人信息（`UPDATE_ACCOUNT`）。注册后用户名唯一且身份证号不可修改。
- 管理员可以添加/修改医生（`ADD_DOCTOR`/`UPDATE_DOCTOR`）和添加/修改排班（`ADD_SCHEDULE`/`UPDATE_SCHEDULE`）。
- 客户端新增按姓名/科室查询医生功能。

调试/扩展命令列表（文本协议）：

- `REGISTER|username|password|fullname|idcard|phone`
- `LOGIN|username|password` 返回 `OK|ROLE`（ROLE 为 `ADMIN` 或 `PATIENT`）
- `DELETE_ACCOUNT`（登录后使用，仅患者）
- `UPDATE_ACCOUNT|password|fullname|phone`（登录后使用，仅患者）
- `SEARCH_NAME|keyword`
- `SEARCH_DEPT|keyword`
- `ADD_DOCTOR|name|dept|info`（管理员）
- `UPDATE_DOCTOR|id|name|dept|info`（管理员）
- `ADD_SCHEDULE|doctorId|yyyy-MM-dd'T'HH:mm|note`（管理员）
- `UPDATE_SCHEDULE|scheduleId|yyyy-MM-dd'T'HH:mm|note`（管理员）
- `LIST_SCHEDULES|doctorId`

注意：为了安全与完整性，客户端在执行需要身份的操作前会要求登录，服务器也会在连接会话中保存当前登录用户与角色并校验权限。

## 新增文件导入/导出与报表（本次更新）

- 导入 Excel（XLS/XLSX）: 管理员可以导入包含 `Doctors` 和 `Schedules` 两个 sheet 的 Excel 文件。
  - `Doctors` sheet 列（从第1行开始，第一行为表头）：
    - 名称 (name)
    - 科室 (dept)
    - 简介 (info)
  - `Schedules` sheet 列（从第1行开始，第一行为表头）：
    - 医生 ID 或 医生姓名（如果填姓名会根据姓名匹配医生）
    - 开始时间（格式 `yyyy-MM-dd'T'HH:mm`，例如 `2025-12-05T09:00`）
    - 结束时间（同上）
    - 容量（整数）
    - 备注
  - 客户端管理员界面点击 `导入医生/排班 (XLS)`，选择文件上传。服务器将返回已添加医生与排班数量。

- 导出预约到 Excel（XLSX）: 管理员点击 `导出预约 (XLS)`，服务器会返回含所有预约记录的 `appointments.xlsx`，客户端下载并保存。
  - 列: `id, doctor_id, doctor_name, dept, patient_username, patient_name, appt_time`

- 生成统计报告 PDF: 管理员点击 `生成统计报表 (PDF)`，服务器返回 `report.pdf`，包含按科室的预约数和各医生的工作量。

## 注意事项与限制（补充）

- Excel 文件通过 JSON 使用 Base64 编码传输，请勿上传不可信任来源的文件。
- 当前并发保护基于单个服务器 JVM 内的锁，适用于单实例服务器。如果需要多进程或集群环境，请考虑使用网络数据库（如 PostgreSQL/MySQL）或 Derby network server 模式以获得跨进程的并发控制。
