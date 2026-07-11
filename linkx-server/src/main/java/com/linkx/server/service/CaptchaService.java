package com.linkx.server.service;

import com.linkx.server.controller.vo.CaptchaVO;

public interface CaptchaService {

    CaptchaVO generate();

    void validate(String captchaId, String captchaCode);
}
