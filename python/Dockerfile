
FROM python:3.9-alpine

COPY . .

WORKDIR ./

COPY requirements.txt .

RUN pip install --no-cache-dir -r requirements.txt

EXPOSE 9999


CMD ["python", "downloader.py"]`