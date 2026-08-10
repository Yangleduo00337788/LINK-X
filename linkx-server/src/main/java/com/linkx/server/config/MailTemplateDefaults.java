package com.linkx.server.config;


/**
 * 作者：yangleduo
 */
/**
 * 内置邮件 HTML 默认模板（占位符：${USERNAME} ${NICKNAME} ${EMAIL} ${CODE} ${EXPIRE_MINUTES} ${YEAR}）。
 * 管理端另提供多套可视化预设，可一键套用后自定义。
 */
public final class MailTemplateDefaults {

    private MailTemplateDefaults() {
    }

    public static final String REGISTER_SUBJECT = "【LinkX】注册验证码";
    public static final String RESET_SUBJECT = "【LinkX】重置密码验证码";
    public static final String WELCOME_SUBJECT = "【LinkX】欢迎加入";

    public static final String REGISTER_HTML = """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"><title>注册验证码</title></head>
            <body style="margin:0;padding:0;background:#f4f6fa;font-family:-apple-system,BlinkMacSystemFont,'PingFang SC','Microsoft YaHei',sans-serif;color:#1f2329;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background:linear-gradient(90deg,#12b7f5 0%,#0d8ed9 100%);"><tr><td style="height:4px;line-height:4px;font-size:0;">&nbsp;</td></tr></table>
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background:#f4f6fa;padding:40px 16px;"><tr><td align="center">
                <table role="presentation" width="560" cellpadding="0" cellspacing="0" border="0" style="max-width:560px;width:100%;background:#fff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(15,23,42,0.08);">
                  <tr><td style="padding:28px 36px 20px;border-bottom:1px solid #f0f2f5;">
                    <div style="font-size:18px;font-weight:600;color:#1f2329;">LinkX</div>
                    <div style="font-size:12px;color:#8f959e;margin-top:2px;">企业级即时通讯与协同平台</div>
                  </td></tr>
                  <tr><td style="padding:32px 36px 8px;">
                    <div style="font-size:22px;font-weight:600;margin-bottom:8px;">注册验证码</div>
                    <div style="font-size:14px;color:#4e5969;line-height:1.7;">您好 <strong>${USERNAME}</strong>，请使用以下验证码完成账号注册：</div>
                  </td></tr>
                  <tr><td style="padding:20px 36px;">
                    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background:linear-gradient(135deg,#eef6ff 0%,#f5f3ff 100%);border:1px solid #e5edff;border-radius:12px;"><tr><td align="center" style="padding:28px 20px;">
                      <div style="font-size:12px;color:#0d8ed9;font-weight:600;letter-spacing:1px;margin-bottom:10px;">VERIFICATION CODE</div>
                      <div style="font-family:Consolas,Monaco,monospace;font-size:36px;font-weight:700;letter-spacing:8px;color:#1f2329;">${CODE}</div>
                      <div style="margin-top:12px;font-size:13px;color:#4e5969;">有效期 <strong style="color:#0d8ed9;">${EXPIRE_MINUTES} 分钟</strong></div>
                    </td></tr></table>
                  </td></tr>
                  <tr><td style="padding:8px 36px 28px;font-size:12px;color:#86909c;line-height:1.7;">如非本人操作，请忽略本邮件。<br>© ${YEAR} LinkX</td></tr>
                </table>
              </td></tr></table>
            </body>
            </html>
            """;

    public static final String RESET_HTML = """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"><title>重置密码</title></head>
            <body style="margin:0;padding:0;background:#f4f6fa;font-family:-apple-system,BlinkMacSystemFont,'PingFang SC','Microsoft YaHei',sans-serif;color:#1f2329;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background:linear-gradient(90deg,#12b7f5 0%,#0d8ed9 100%);"><tr><td style="height:4px;line-height:4px;font-size:0;">&nbsp;</td></tr></table>
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background:#f4f6fa;padding:40px 16px;"><tr><td align="center">
                <table role="presentation" width="560" cellpadding="0" cellspacing="0" border="0" style="max-width:560px;width:100%;background:#fff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(15,23,42,0.08);">
                  <tr><td style="padding:28px 36px 20px;border-bottom:1px solid #f0f2f5;">
                    <div style="font-size:18px;font-weight:600;color:#1f2329;">LinkX</div>
                    <div style="font-size:12px;color:#8f959e;margin-top:2px;">企业级即时通讯与协同平台</div>
                  </td></tr>
                  <tr><td style="padding:32px 36px 8px;">
                    <div style="font-size:22px;font-weight:600;margin-bottom:8px;">重置密码验证码</div>
                    <div style="font-size:14px;color:#4e5969;line-height:1.7;">您好 <strong>${USERNAME}</strong>，您正在重置密码，请输入下方验证码：</div>
                  </td></tr>
                  <tr><td style="padding:20px 36px;">
                    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background:linear-gradient(135deg,#eef6ff 0%,#f5f3ff 100%);border:1px solid #e5edff;border-radius:12px;"><tr><td align="center" style="padding:28px 20px;">
                      <div style="font-size:12px;color:#0d8ed9;font-weight:600;letter-spacing:1px;margin-bottom:10px;">VERIFICATION CODE</div>
                      <div style="font-family:Consolas,Monaco,monospace;font-size:36px;font-weight:700;letter-spacing:8px;color:#1f2329;">${CODE}</div>
                      <div style="margin-top:12px;font-size:13px;color:#4e5969;">有效期 <strong style="color:#0d8ed9;">${EXPIRE_MINUTES} 分钟</strong></div>
                    </td></tr></table>
                  </td></tr>
                  <tr><td style="padding:8px 36px 28px;font-size:12px;color:#86909c;line-height:1.7;">请勿将验证码告知他人。如非本人操作，请忽略本邮件。<br>© ${YEAR} LinkX</td></tr>
                </table>
              </td></tr></table>
            </body>
            </html>
            """;

    public static final String WELCOME_HTML = """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"><title>欢迎加入</title></head>
            <body style="margin:0;padding:0;background:#f4f6fa;font-family:-apple-system,BlinkMacSystemFont,'PingFang SC','Microsoft YaHei',sans-serif;color:#1f2329;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background:linear-gradient(90deg,#12b7f5 0%,#0d8ed9 100%);"><tr><td style="height:4px;line-height:4px;font-size:0;">&nbsp;</td></tr></table>
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background:#f4f6fa;padding:40px 16px;"><tr><td align="center">
                <table role="presentation" width="560" cellpadding="0" cellspacing="0" border="0" style="max-width:560px;width:100%;background:#fff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(15,23,42,0.08);">
                  <tr><td style="padding:28px 36px 20px;border-bottom:1px solid #f0f2f5;">
                    <div style="font-size:18px;font-weight:600;color:#1f2329;">LinkX</div>
                    <div style="font-size:12px;color:#8f959e;margin-top:2px;">企业级即时通讯与协同平台</div>
                  </td></tr>
                  <tr><td style="padding:36px 36px 12px;" align="center">
                    <div style="width:56px;height:56px;border-radius:50%;background:linear-gradient(135deg,#12b7f5,#6366f1);color:#fff;line-height:56px;font-size:24px;font-weight:700;margin:0 auto 16px;">✓</div>
                    <div style="font-size:22px;font-weight:600;margin-bottom:8px;">欢迎加入 LinkX</div>
                    <div style="font-size:14px;color:#4e5969;line-height:1.7;">您好 <strong>${NICKNAME}</strong>，账号 <strong>${USERNAME}</strong> 已注册成功。</div>
                  </td></tr>
                  <tr><td style="padding:16px 36px 28px;">
                    <div style="background:#f7f8fa;border-radius:12px;padding:16px 20px;font-size:13px;color:#4e5969;line-height:1.7;">
                      绑定邮箱：${EMAIL}<br>打开客户端即可开始使用。
                    </div>
                    <div style="margin-top:20px;font-size:12px;color:#c9cdd4;">© ${YEAR} LinkX</div>
                  </td></tr>
                </table>
              </td></tr></table>
            </body>
            </html>
            """;
}
