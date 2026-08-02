package com.linkx.server.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.controller.vo.LocationPlaceVO;
import com.linkx.server.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LocationServiceImpl 位置搜索")
class LocationServiceImplTest {

    @Mock HttpClient httpClient;
    @Mock HttpResponse<String> httpResponse;

    private LocationServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        service = new LocationServiceImpl(new ObjectMapper());
        Field f = LocationServiceImpl.class.getDeclaredField("httpClient");
        f.setAccessible(true);
        f.set(service, httpClient);
    }

    @Test
    @DisplayName("空关键字返回空列表")
    void blankKeyword() {
        assertTrue(service.search("  ", 5).isEmpty());
        assertTrue(service.search(null, 5).isEmpty());
    }

    @Test
    @DisplayName("Nominatim 成功解析")
    @SuppressWarnings("unchecked")
    void searchOk() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("""
                [{"display_name":"Shanghai, China","lat":"31.2","lon":"121.5",
                  "address":{"city":"Shanghai","name":"外滩"}}]
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        List<LocationPlaceVO> list = service.search("shanghai", 20);
        assertEquals(1, list.size());
        assertEquals("外滩", list.get(0).getName());
        assertEquals(31.2, list.get(0).getLat());
        assertEquals(121.5, list.get(0).getLon());
    }

    @Test
    @DisplayName("上游 5xx 抛 502")
    @SuppressWarnings("unchecked")
    void upstreamError() throws Exception {
        when(httpResponse.statusCode()).thenReturn(503);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        CustomException ex = assertThrows(CustomException.class, () -> service.search("x", 3));
        assertEquals(502, ex.getCode());
    }

    @Test
    @DisplayName("网络异常抛 502")
    @SuppressWarnings("unchecked")
    void networkError() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new java.io.IOException("timeout"));
        CustomException ex = assertThrows(CustomException.class, () -> service.search("x", 3));
        assertEquals(502, ex.getCode());
    }
}
