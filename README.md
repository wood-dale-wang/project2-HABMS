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
