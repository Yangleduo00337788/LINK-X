<!-- 作者：yangleduo -->
# IP 归属地数据（ip2region）

将离线库文件放到本目录（或通过配置指定路径）后，管理端登录日志会展示 IP 归属地。

## 获取数据文件

从 [ip2region](https://github.com/lionsoul2014/ip2region) 下载 `ip2region.xdb`（2.x 数据文件），保存为：

```text
linkx-server/src/main/resources/ip2region/ip2region.xdb
```

也可放到任意路径，并在配置中指定：

```yaml
linkx:
  ip-geo:
    xdb-path: D:/data/ip2region.xdb
```

未配置数据文件时：内网 IP 显示「内网」，公网 IP 显示「未知」。
