FROM emmercm/libtorrent:2.0-alpine AS libtorrent_env

FROM python:3.10-alpine

RUN apk add --no-cache libstdc++ boost-system boost-python3

COPY --from=libtorrent_env /usr/lib/ /usr/lib/

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

EXPOSE 9999

CMD ["uvicorn", "wsgi:app", "--host", "0.0.0.0", "--port", "9999"]