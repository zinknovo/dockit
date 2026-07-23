FROM eclipse-temurin:17-jre

# 维护者信息
MAINTAINER qxk

# 设定时区
RUN ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && echo "Asia/Shanghai" > /etc/timezone

# 安装 git（文档版本控制依赖）
RUN apt-get update && apt-get install -y --no-install-recommends git && rm -rf /var/lib/apt/lists/*

# 复制jar包到容器内
COPY target/document-service-1.0.0.jar /app/document-service.jar

# 设定工作目录
WORKDIR /app

# 暴露端口
EXPOSE 8084

# 启动应用
CMD ["java", "-jar", "document-service.jar"]
