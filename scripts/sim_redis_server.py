#!/usr/bin/env python3
"""Tiny dependency-free Redis RESP server for Phase 1 phone/PC tests.

It implements only the commands needed by `sim_vehicle_redis.py` and the Android
GET-only client: PING, AUTH, SELECT, GET, SET, DEL, EXISTS, KEYS, DBSIZE, QUIT.
State is in-memory and intentionally resets when the process exits.
"""
from __future__ import annotations

import argparse
import fnmatch
import socketserver
import threading
from typing import Dict, List, Optional, Union

BytesOrList = Union[bytes, List[bytes]]


class RedisState:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._values: Dict[bytes, bytes] = {}

    def get(self, key: bytes) -> Optional[bytes]:
        with self._lock:
            return self._values.get(key)

    def set(self, key: bytes, value: bytes) -> None:
        with self._lock:
            self._values[key] = value

    def delete(self, *keys: bytes) -> int:
        removed = 0
        with self._lock:
            for key in keys:
                if key in self._values:
                    del self._values[key]
                    removed += 1
        return removed

    def exists(self, *keys: bytes) -> int:
        with self._lock:
            return sum(1 for key in keys if key in self._values)

    def keys(self, pattern: bytes) -> List[bytes]:
        text_pattern = pattern.decode("utf-8", errors="replace")
        with self._lock:
            return sorted(
                key for key in self._values
                if fnmatch.fnmatch(key.decode("utf-8", errors="replace"), text_pattern)
            )

    def dbsize(self) -> int:
        with self._lock:
            return len(self._values)


class RespError(Exception):
    pass


class Handler(socketserver.BaseRequestHandler):
    def handle(self) -> None:
        while True:
            try:
                command = self._read_command()
            except EOFError:
                return
            except RespError as exc:
                self._write_error(str(exc))
                continue
            if not command:
                self._write_error("empty command")
                continue
            name = command[0].upper()
            args = command[1:]
            try:
                keep_going = self._dispatch(name, args)
            except Exception as exc:  # keep simulator debuggable instead of killing the process
                self._write_error(str(exc))
                keep_going = True
            if not keep_going:
                return

    @property
    def state(self) -> RedisState:
        return self.server.state  # type: ignore[attr-defined]

    def _dispatch(self, name: bytes, args: List[bytes]) -> bool:
        if name == b"PING":
            self._write_simple(args[0] if args else b"PONG")
        elif name in (b"AUTH", b"SELECT"):
            self._write_simple(b"OK")
        elif name == b"GET":
            self._require_arg_count(name, args, 1)
            self._write_bulk(self.state.get(args[0]))
        elif name == b"SET":
            self._require_min_args(name, args, 2)
            self.state.set(args[0], args[1])
            self._write_simple(b"OK")
        elif name == b"DEL":
            self._write_integer(self.state.delete(*args))
        elif name == b"EXISTS":
            self._write_integer(self.state.exists(*args))
        elif name == b"KEYS":
            self._require_arg_count(name, args, 1)
            self._write_array(self.state.keys(args[0]))
        elif name == b"DBSIZE":
            self._write_integer(self.state.dbsize())
        elif name == b"QUIT":
            self._write_simple(b"OK")
            return False
        else:
            self._write_error(f"unsupported command {name.decode('utf-8', errors='replace')}")
        return True

    def _read_command(self) -> List[bytes]:
        first = self._read_exact(1)
        if first != b"*":
            raise RespError(f"expected RESP array, got {first!r}")
        count = int(self._read_line())
        command: List[bytes] = []
        for _ in range(count):
            marker = self._read_exact(1)
            if marker != b"$":
                raise RespError(f"expected bulk string, got {marker!r}")
            length = int(self._read_line())
            if length < 0:
                command.append(b"")
                continue
            payload = self._read_exact(length)
            if self._read_exact(2) != b"\r\n":
                raise RespError("malformed bulk string ending")
            command.append(payload)
        return command

    def _read_line(self) -> bytes:
        chunks = []
        while True:
            byte = self._read_exact(1)
            if byte == b"\r":
                if self._read_exact(1) != b"\n":
                    raise RespError("malformed line ending")
                return b"".join(chunks)
            chunks.append(byte)

    def _read_exact(self, length: int) -> bytes:
        data = b""
        while len(data) < length:
            chunk = self.request.recv(length - len(data))
            if not chunk:
                raise EOFError
            data += chunk
        return data

    def _write_simple(self, value: bytes) -> None:
        self.request.sendall(b"+" + value + b"\r\n")

    def _write_error(self, value: str) -> None:
        self.request.sendall(b"-" + value.encode("utf-8") + b"\r\n")

    def _write_integer(self, value: int) -> None:
        self.request.sendall(b":" + str(value).encode("ascii") + b"\r\n")

    def _write_bulk(self, value: Optional[bytes]) -> None:
        if value is None:
            self.request.sendall(b"$-1\r\n")
            return
        self.request.sendall(b"$" + str(len(value)).encode("ascii") + b"\r\n" + value + b"\r\n")

    def _write_array(self, values: List[bytes]) -> None:
        self.request.sendall(b"*" + str(len(values)).encode("ascii") + b"\r\n")
        for value in values:
            self._write_bulk(value)

    def _require_arg_count(self, name: bytes, args: List[bytes], count: int) -> None:
        if len(args) != count:
            raise RespError(f"{name.decode()} requires {count} args")

    def _require_min_args(self, name: bytes, args: List[bytes], count: int) -> None:
        if len(args) < count:
            raise RespError(f"{name.decode()} requires at least {count} args")


class ThreadingRedisServer(socketserver.ThreadingTCPServer):
    allow_reuse_address = True

    def __init__(self, server_address, request_handler_class):
        super().__init__(server_address, request_handler_class)
        self.state = RedisState()


def main() -> int:
    parser = argparse.ArgumentParser(description="Start dependency-free simulated Redis RESP server")
    parser.add_argument("--host", default="0.0.0.0", help="Bind address; use 0.0.0.0 for phone access")
    parser.add_argument("--port", type=int, default=6379, help="TCP port")
    args = parser.parse_args()

    with ThreadingRedisServer((args.host, args.port), Handler) as server:
        host, port = server.server_address
        print(f"sim Redis server listening on {host}:{port} (in-memory, no auth)", flush=True)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            print("\nshutting down", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
