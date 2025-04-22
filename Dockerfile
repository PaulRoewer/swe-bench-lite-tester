FROM ubuntu:22.04

# Set timezone non-interactively
ENV DEBIAN_FRONTEND=noninteractive
ENV TZ=Etc/UTC

# Install system dependencies and Java 21
RUN apt-get update && apt-get install -y \
    docker.io \
    wget \
    git \
    curl \
    gnupg \
    build-essential \
    libffi-dev \
    libtiff-dev \
    python3 \
    python3-pip \
    python-is-python3 \
    jq \
    locales \
    locales-all \
    tzdata && \
    rm -rf /var/lib/apt/lists/*

# Install Python dependencies
RUN pip install --upgrade pip && \
    pip install "numpy<2" astropy asdf hypothesis pytest pipreqs django

# Install Java 21 (Temurin)
RUN wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor > /etc/apt/trusted.gpg.d/adoptium.gpg && \
    echo "deb https://packages.adoptium.net/artifactory/deb focal main" > /etc/apt/sources.list.d/adoptium.list && \
    apt-get update && \
    apt-get install -y temurin-21-jdk && \
    rm -rf /var/lib/apt/lists/*

# Install Miniconda
RUN wget https://repo.anaconda.com/miniconda/Miniconda3-latest-Linux-x86_64.sh -O miniconda.sh && \
    bash miniconda.sh -b -p /opt/miniconda && \
    rm miniconda.sh

ENV PATH=/opt/miniconda/bin:$PATH

# Install SWE-Bench-Harness
RUN git clone https://github.com/SWE-bench/SWE-bench.git /opt/swebench && \
    pip install -e /opt/swebench

# Configure Git to avoid "dubious ownership" errors
RUN git config --global core.autocrlf false && \
    git config --global --add safe.directory '*'

# App directory
WORKDIR /app

# Copy Spring Boot jar
COPY target/swe-bench-lite-tester-0.0.1-SNAPSHOT.jar app.jar

# Expose REST port
EXPOSE 8080

# Start Java service
CMD ["java", "-Dfile.encoding=UTF-8", "-jar", "app.jar"]
