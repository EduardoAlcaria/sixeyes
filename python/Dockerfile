FROM emmercm/libtorrent:2.0-alpine AS libtorrent_env

FROM python:3.10-alpine

RUN apk add --no-cache libstdc++ boost-system boost-python3

COPY --from=libtorrent_env /usr/lib/ /usr/lib/

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# Drop root — run as unprivileged user
RUN adduser -D -H sixeyes && chown -R sixeyes /app
USER sixeyes

EXPOSE 9999

# Bind on all interfaces inside the container so the Java service can reach it
# over the docker network. The port is NOT published to the host (see compose).
CMD ["uvicorn", "wsgi:app", "--host", "0.0.0.0", "--port", "9999"]