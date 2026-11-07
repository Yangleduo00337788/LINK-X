# 作者：yangleduo
# 本地静态 MinIO 二进制，避免压测时拉取 docker.io/minio（镜像站常 denied）
FROM scratch
COPY minio /minio
EXPOSE 9000 9001
ENTRYPOINT ["/minio"]
CMD ["server", "/data", "--address", ":9000", "--console-address", ":9001"]
