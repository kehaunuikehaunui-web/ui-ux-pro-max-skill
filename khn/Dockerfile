FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 KHN_HOST=0.0.0.0 KHN_PORT=8000 KHN_DATA_DIR=/app/data
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt && useradd --create-home --uid 1000 khn && mkdir -p /app/data && chown khn /app/data
COPY engine ./engine
COPY web/index.html ./web/index.html
USER khn
EXPOSE 8000
HEALTHCHECK --interval=60s --timeout=5s --start-period=60s CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/healthz', timeout=4)"
CMD ["python", "-m", "engine"]
