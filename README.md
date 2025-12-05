# 项目：医院预约系统 (HABMS)

说明：

- 架构：C/S（服务器 + Swing 客户端）
- 构建工具：Maven
- 数据库：JavaDB (Apache Derby) 嵌入式

快速运行：

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
