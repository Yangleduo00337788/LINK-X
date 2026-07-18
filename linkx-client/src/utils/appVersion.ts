/**
 * LinkX 客户端构建版本号。
 * 用于"检查更新"接口的 current 参数；服务端比对后返回 hasUpdate。
 * <p>
 * 与后端 linkx.app.version 默认值保持一致。
 * 升级客户端构建时，需要同步修改此处与服务端配置。
 * </p>
 */
export const APP_CLIENT_VERSION = '1.0.0'