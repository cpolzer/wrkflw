#!/usr/bin/env python3
"""
Proxies the Docker Unix socket and rewrites any API version <= 1.39 in URL paths
to 1.45, allowing old clients (e.g. Testcontainers shaded docker-java) to work
with Docker 29.x (which dropped support for API < 1.40).

Usage:
  python3 docker-api-proxy.py [proxy_socket] [real_socket]

Defaults:
  proxy_socket = /tmp/docker-proxy.sock
  real_socket  = /var/run/docker.sock
"""

import asyncio
import re
import os
import sys

PROXY_SOCK = sys.argv[1] if len(sys.argv) > 1 else "/tmp/docker-proxy.sock"
REAL_SOCK = sys.argv[2] if len(sys.argv) > 2 else "/var/run/docker.sock"

VERSION_RE = re.compile(rb"^((?:GET|POST|PUT|DELETE|HEAD|PATCH|OPTIONS) )/v1\.([0-9]+)/")
REPLACEMENT_VERSION = b"1.45"


def rewrite_request(data: bytes) -> bytes:
    def fix(m: re.Match) -> bytes:
        minor = int(m.group(2))
        if minor < 40:
            return m.group(1) + b"/v" + REPLACEMENT_VERSION + b"/"
        return m.group(0)

    return VERSION_RE.sub(fix, data, count=1)


async def pipe(reader: asyncio.StreamReader, writer: asyncio.StreamWriter, rewrite: bool):
    try:
        while True:
            data = await reader.read(65536)
            if not data:
                break
            if rewrite:
                data = rewrite_request(data)
            writer.write(data)
            await writer.drain()
    except (asyncio.IncompleteReadError, ConnectionResetError):
        pass
    finally:
        try:
            writer.close()
            await writer.wait_closed()
        except Exception:
            pass


async def handle(client_r: asyncio.StreamReader, client_w: asyncio.StreamWriter):
    try:
        docker_r, docker_w = await asyncio.open_unix_connection(REAL_SOCK)
        await asyncio.gather(
            pipe(client_r, docker_w, rewrite=True),
            pipe(docker_r, client_w, rewrite=False),
        )
    except Exception as e:
        client_w.close()


async def main():
    if os.path.exists(PROXY_SOCK):
        os.unlink(PROXY_SOCK)

    server = await asyncio.start_unix_server(handle, path=PROXY_SOCK)
    os.chmod(PROXY_SOCK, 0o660)
    print(f"Docker API proxy listening on {PROXY_SOCK} → {REAL_SOCK}", flush=True)

    async with server:
        await server.serve_forever()


asyncio.run(main())
