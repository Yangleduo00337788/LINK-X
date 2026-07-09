// HTTP 客户端库
import axios from 'axios';

// 创建 axios 实例，统一配置后端地址与超时
export const apiClient = axios.create({
  baseURL: 'http://localhost:8080/api', // 与后端 context-path /api 对齐
  timeout: 10000                         // 10 秒请求超时
});

// 请求拦截器：在每次请求发出前自动附加 JWT
apiClient.interceptors.request.use(config => {
  // 从 localStorage 读取登录时保存的 AccessToken
  const token = localStorage.getItem('accessToken');
  if (token) {
    // 按后端 LoginInterceptor 约定，使用 Bearer  scheme
    config.headers['Authorization'] = `Bearer ${token}`;
  }
  return config; // 返回修改后的 config，继续发送请求
});

// 响应拦截器：统一处理成功/失败响应
apiClient.interceptors.response.use(
  // 成功：直接返回 response.data，对应后端 Result<T> 结构
  response => response.data,
  // 失败：重点处理 401 未授权
  error => {
    if (error.response?.status === 401) {
      // Token 失效，Hash 路由跳回登录页
      window.location.hash = '#/';
      // 清除本地失效 Token，避免循环 401
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
    }
    // 继续抛出错误，供调用方 catch 处理
    return Promise.reject(error);
  }
);
