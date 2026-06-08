#!/usr/bin/env python3
"""Write/read Phase 1 simulated vehicle protobuf payloads into Redis.

Works with the dependency-free `scripts/sim_redis_server.py` and with real Redis
servers that support the basic RESP commands used here.
"""
from __future__ import annotations

import argparse
import fnmatch
import socket
import struct
from typing import Dict, Iterable, List, Optional, Tuple, Union

DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 6379

KEY_SPEED = "BC_Veh_Spd"
KEY_DCU_INFO_1 = "DCU_INFO_1"
KEY_DCU_INFO_2 = "DCU_INFO_2"
KEY_BATTERY = "DCU_Battery_St"
KEY_RANGE = "DCU_INFO"
KEY_L2 = "DCU_L2_St"
KEY_AC_TEMP = "ACM_INF2"
KEY_AC_STATE = "ACM_INF4"
KEY_BODY = "BC_AutoD_Veh_St"
KEY_TPMS = "TPMS_INFO"
KEY_LOCATION = "Sensor_Location"
KEY_OBSTACLES = "Sensor_Mmobstacles"
KEY_TRAFFIC = "Sensor_Trafficlightlist"
KEY_MAIN_OBSTACLE = "PFC_Main_Obstacle_INF"
KEY_SAM = "Sam"  # simulator key pending real Redis confirmation

DEFAULT_KEYS = [
    KEY_SPEED,
    KEY_DCU_INFO_1,
    KEY_DCU_INFO_2,
    KEY_BATTERY,
    KEY_RANGE,
    KEY_L2,
    KEY_AC_TEMP,
    KEY_AC_STATE,
    KEY_BODY,
    KEY_TPMS,
    KEY_LOCATION,
    KEY_OBSTACLES,
    KEY_TRAFFIC,
    KEY_MAIN_OBSTACLE,
    KEY_SAM,
]

COLOR = {"red": 1, "yellow": 2, "green": 3}
OBSTACLE_TYPE = {"invalid": 0, "pedestrian": 1, "vehicle": 2}
EVENT_TYPE = {"unknown": 0, "start": 1, "ongoing": 2, "end": 3}


def tag(number: int, wire_type: int) -> bytes:
    return varint((number << 3) | wire_type)


def varint(value: int) -> bytes:
    value &= (1 << 64) - 1
    out = bytearray()
    while True:
        if value & ~0x7F == 0:
            out.append(value)
            return bytes(out)
        out.append((value & 0x7F) | 0x80)
        value >>= 7


def enum(number: int, value: int) -> bytes:
    return tag(number, 0) + varint(value)


def int32(number: int, value: int) -> bytes:
    return tag(number, 0) + varint(value)


def uint32(number: int, value: int) -> bytes:
    return tag(number, 0) + varint(value)


def float32(number: int, value: float) -> bytes:
    return tag(number, 5) + struct.pack("<f", value)


def double64(number: int, value: float) -> bytes:
    return tag(number, 1) + struct.pack("<d", value)


def length_delimited(number: int, payload: bytes) -> bytes:
    return tag(number, 2) + varint(len(payload)) + payload


def string(number: int, value: str) -> bytes:
    return length_delimited(number, value.encode("utf-8"))


def speed(value: float) -> bytes:
    return float32(1, value)


def dcu_info_1(gear: int = 3, parking: int = 0) -> bytes:
    return enum(1, gear) + enum(2, 4) + enum(3, parking) + enum(4, 0)


def dcu_info_2(
    auto_limit: int = 0,
    emergency: int = 0,
    low_fault: int = 0,
    takeover: int = 0,
    drive_mode: int = 4,
    auto_out: int = 0,
    brake_fault: int = 0,
    brake_status: int = 0,
    high_fault: int = 0,
) -> bytes:
    return b"".join([
        enum(1, auto_limit),
        enum(2, emergency),
        enum(3, low_fault),
        enum(4, takeover),
        enum(5, drive_mode),
        enum(6, auto_out),
        enum(9, brake_fault),
        enum(10, brake_status),
        enum(11, high_fault),
    ])


def battery(soc: float) -> bytes:
    return float32(1, 612.0) + float32(2, 8.5) + float32(3, soc)


def vehicle_range(km: float) -> bytes:
    return float32(1, km)


def l2_state(
    acc_status: int = 4,
    acc_mode: int = 2,
    acc_fail: int = 0,
    acc_quit: int = 0,
    lka_status: int = 3,
    lka_quit: int = 0,
    lka_fail: int = 0,
    l2_mode: int = 3,
) -> bytes:
    return b"".join([
        enum(1, acc_status),
        enum(2, acc_mode),
        enum(3, acc_fail),
        enum(4, acc_quit),
        enum(5, lka_status),
        enum(6, lka_quit),
        enum(7, lka_fail),
        enum(9, l2_mode),
    ])


def ac_temperature(in_car: float = 26.5, out_car: float = 30.0) -> bytes:
    return float32(1, in_car) + float32(2, out_car)


def ac_state(power: int = 1, mode: int = 4, fan: int = 2, temp: float = 24.0) -> bytes:
    return enum(1, power) + enum(2, mode) + enum(3, fan) + float32(4, temp)


def body_state(front: int = 2, mid: int = 2, horn: int = 0, wiper: int = 0) -> bytes:
    return enum(3, front) + enum(4, mid) + enum(8, horn) + enum(23, wiper)


def tpms(
    pressure: int = 830,
    temp: float = 36.0,
    high_temp: int = 0,
    leak: int = 0,
    lost: int = 0,
    pressure_alarm: int = 2,
) -> bytes:
    return b"".join([
        enum(1, 0),
        uint32(2, pressure),
        float32(3, temp),
        enum(4, high_temp),
        enum(5, leak),
        enum(6, lost),
        enum(7, pressure_alarm),
    ])


def location() -> bytes:
    return b"".join([
        double64(1, 1_717_820_800.0),
        double64(2, 106.5516),
        double64(3, 29.5630),
        double64(7, 92.0),
        double64(8, 3.47),
    ])


def obstacles(kind: int = 2, x: float = 18.2, y: float = -0.5, confidence: float = 0.88) -> bytes:
    item = b"".join([
        int32(1, 101),
        double64(2, x),
        double64(3, y),
        double64(8, 2.1),
        int32(15, kind),
        double64(16, confidence),
    ])
    return double64(1, 1_717_820_800.0) + int32(2, 1) + length_delimited(3, item)


def traffic(color: int = 3, confidence: float = 0.93) -> bytes:
    item = int32(1, color) + double64(2, confidence) + int32(3, 120) + int32(4, 48) + int32(5, 32) + int32(6, 18)
    return double64(1, 1_717_820_800.0) + int32(2, 1) + length_delimited(3, item)


def main_obstacle(
    kind: int = 2,
    x: float = 18.2,
    y: float = -0.5,
    rel_vx: float = 2.1,
    camera_fault: int = 0,
    radar_fault: int = 0,
    vehicle_fault: int = 0,
    fusion_fault: int = 0,
) -> bytes:
    return b"".join([
        enum(1, kind),
        float32(2, x),
        float32(3, y),
        float32(4, rel_vx),
        float32(5, 0.0),
        enum(6, camera_fault),
        enum(7, radar_fault),
        enum(8, vehicle_fault),
        enum(9, fusion_fault),
    ])


def sam(scene: int = 1, event: int = 2, count: int = 2, guide: int = 1, feedback: int = 3, behavior: int = 1) -> bytes:
    collaborative = int32(1, 2001) + int32(2, 1)
    return b"".join([
        double64(1, 1_717_820_800.0),
        int32(2, scene),
        int32(3, 1001),
        string(4, "渝A-SIM01"),
        string(5, "L4"),
        int32(6, 4),
        int32(7, 3),
        double64(8, 1.5),
        double64(9, 0.2),
        double64(10, 3.47),
        int32(11, count),
        length_delimited(12, collaborative),
        int32(13, behavior),
        int32(14, 4),
        int32(15, guide),
        int32(16, feedback),
        int32(17, behavior),
        enum(18, event),
        double64(19, 1_717_820_700_000.0),
        double64(20, 0.0),
        int32(21, 42),
    ])


def default_payloads() -> Dict[str, bytes]:
    return {
        KEY_SPEED: speed(12.5),
        KEY_DCU_INFO_1: dcu_info_1(),
        KEY_DCU_INFO_2: dcu_info_2(),
        KEY_BATTERY: battery(76.0),
        KEY_RANGE: vehicle_range(128.0),
        KEY_L2: l2_state(),
        KEY_AC_TEMP: ac_temperature(),
        KEY_AC_STATE: ac_state(),
        KEY_BODY: body_state(),
        KEY_TPMS: tpms(),
        KEY_LOCATION: location(),
        KEY_OBSTACLES: obstacles(),
        KEY_TRAFFIC: traffic(),
        KEY_MAIN_OBSTACLE: main_obstacle(),
        KEY_SAM: sam(),
    }


class RedisClient:
    def __init__(self, host: str, port: int, timeout: float = 2.0) -> None:
        self.host = host
        self.port = port
        self.timeout = timeout

    def command(self, *parts: Union[str, bytes]) -> object:
        with socket.create_connection((self.host, self.port), timeout=self.timeout) as sock:
            sock.settimeout(self.timeout)
            encoded = []
            for part in parts:
                encoded.append(part if isinstance(part, bytes) else part.encode("utf-8"))
            payload = b"*" + str(len(encoded)).encode() + b"\r\n"
            for part in encoded:
                payload += b"$" + str(len(part)).encode() + b"\r\n" + part + b"\r\n"
            sock.sendall(payload)
            return self._read_reply(sock)

    def _read_reply(self, sock: socket.socket) -> object:
        marker = self._read_exact(sock, 1)
        if marker == b"+":
            return self._read_line(sock).decode("utf-8")
        if marker == b"-":
            raise RuntimeError(self._read_line(sock).decode("utf-8"))
        if marker == b":":
            return int(self._read_line(sock))
        if marker == b"$":
            length = int(self._read_line(sock))
            if length < 0:
                return None
            payload = self._read_exact(sock, length)
            assert self._read_exact(sock, 2) == b"\r\n"
            return payload
        if marker == b"*":
            count = int(self._read_line(sock))
            return [self._read_reply(sock) for _ in range(count)]
        raise RuntimeError(f"unsupported RESP marker {marker!r}")

    def _read_line(self, sock: socket.socket) -> bytes:
        chunks = []
        while True:
            byte = self._read_exact(sock, 1)
            if byte == b"\r":
                assert self._read_exact(sock, 1) == b"\n"
                return b"".join(chunks)
            chunks.append(byte)

    def _read_exact(self, sock: socket.socket, length: int) -> bytes:
        data = b""
        while len(data) < length:
            chunk = sock.recv(length - len(data))
            if not chunk:
                raise EOFError("Redis connection closed")
            data += chunk
        return data

    def set(self, key: str, value: bytes) -> None:
        self.command("SET", key, value)

    def get(self, key: str) -> Optional[bytes]:
        value = self.command("GET", key)
        return value if isinstance(value, bytes) else None

    def delete(self, key: str) -> None:
        self.command("DEL", key)

    def keys(self, pattern: str = "*") -> List[str]:
        values = self.command("KEYS", pattern)
        if not isinstance(values, list):
            return []
        return [item.decode("utf-8", errors="replace") if isinstance(item, bytes) else str(item) for item in values]


def write_defaults(client: RedisClient) -> None:
    for key, payload in default_payloads().items():
        client.set(key, payload)


def print_status(client: RedisClient) -> None:
    keys = client.keys("*")
    print(f"keys={len(keys)}")
    for key in sorted(keys):
        value = client.get(key)
        print(f"{key}: {0 if value is None else len(value)} bytes")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Simulate vehicle Redis/protobuf values")
    parser.add_argument("--host", default=DEFAULT_HOST)
    parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("defaults", help="write default payloads for all Phase 1 keys")
    sub.add_parser("status", help="list keys and payload lengths")

    p = sub.add_parser("set-speed"); p.add_argument("--value", type=float, required=True)
    p = sub.add_parser("set-battery"); p.add_argument("--soc", type=float, required=True)
    p = sub.add_parser("set-range"); p.add_argument("--km", type=float, required=True)
    p = sub.add_parser("set-ac"); p.add_argument("--power", choices=["on", "off"], default="on"); p.add_argument("--temp", type=float, default=24.0); p.add_argument("--fan", type=int, default=2); p.add_argument("--mode", type=int, default=4)
    p = sub.add_parser("set-door"); p.add_argument("--front", choices=["open", "closed"], default="closed"); p.add_argument("--mid", choices=["open", "closed"], default="closed")
    p = sub.add_parser("set-tire"); p.add_argument("--pressure", type=int, default=830); p.add_argument("--temp", type=float, default=36.0); p.add_argument("--alarm", choices=["none", "leak", "low", "high_temp", "lost"], default="none")
    p = sub.add_parser("set-traffic-light"); p.add_argument("--color", choices=sorted(COLOR), required=True)
    p = sub.add_parser("set-obstacle"); p.add_argument("--type", choices=sorted(OBSTACLE_TYPE), default="vehicle"); p.add_argument("--x", type=float, default=18.2); p.add_argument("--y", type=float, default=-0.5)
    p = sub.add_parser("set-warning")
    p.add_argument("--auto-limit", type=int, default=0, help="DCU_INFO_2 field 1 enum, e.g. 32 door not closed")
    p.add_argument("--emergency", type=int, default=0)
    p.add_argument("--low-fault", type=int, default=0)
    p.add_argument("--takeover", type=int, default=0)
    p.add_argument("--drive-mode", type=int, default=4)
    p.add_argument("--auto-out", type=int, default=0)
    p.add_argument("--brake-fault", type=int, default=0)
    p.add_argument("--high-fault", type=int, default=0)
    p = sub.add_parser("set-l2")
    p.add_argument("--acc-status", type=int, default=4)
    p.add_argument("--acc-fail", type=int, default=0)
    p.add_argument("--acc-quit", type=int, default=0)
    p.add_argument("--lka-status", type=int, default=3)
    p.add_argument("--lka-fail", type=int, default=0)
    p.add_argument("--lka-quit", type=int, default=0)
    p = sub.add_parser("set-perception-fault")
    p.add_argument("--camera", type=int, default=0)
    p.add_argument("--radar", type=int, default=0)
    p.add_argument("--vehicle", type=int, default=0)
    p.add_argument("--fusion", type=int, default=0)
    p = sub.add_parser("set-sam")
    p.add_argument("--scene", type=int, default=1)
    p.add_argument("--event", choices=sorted(EVENT_TYPE), default="ongoing")
    p.add_argument("--count", type=int, default=2)
    p.add_argument("--guide", type=int, default=1)
    p.add_argument("--feedback", type=int, default=3)
    p.add_argument("--behavior", type=int, default=1)
    p = sub.add_parser("corrupt"); p.add_argument("--key", required=True)
    p = sub.add_parser("delete"); p.add_argument("--key", required=True)
    p = sub.add_parser("get"); p.add_argument("--key", required=True)
    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    client = RedisClient(args.host, args.port)

    if args.command == "defaults":
        write_defaults(client); print("wrote default vehicle protobuf payloads")
    elif args.command == "status":
        print_status(client)
    elif args.command == "set-speed":
        client.set(KEY_SPEED, speed(args.value)); print(f"{KEY_SPEED} speed={args.value}")
    elif args.command == "set-battery":
        client.set(KEY_BATTERY, battery(args.soc)); print(f"{KEY_BATTERY} soc={args.soc}")
    elif args.command == "set-range":
        client.set(KEY_RANGE, vehicle_range(args.km)); print(f"{KEY_RANGE} km={args.km}")
    elif args.command == "set-ac":
        client.set(KEY_AC_STATE, ac_state(power=1 if args.power == "on" else 0, mode=args.mode, fan=args.fan, temp=args.temp)); print("updated AC state")
    elif args.command == "set-door":
        client.set(KEY_BODY, body_state(front=1 if args.front == "open" else 2, mid=1 if args.mid == "open" else 2)); print("updated door state")
    elif args.command == "set-tire":
        client.set(KEY_TPMS, tpms(pressure=args.pressure, temp=args.temp, high_temp=1 if args.alarm == "high_temp" else 0, leak=1 if args.alarm == "leak" else 0, lost=1 if args.alarm == "lost" else 0, pressure_alarm=3 if args.alarm == "low" else 2)); print("updated tire state")
    elif args.command == "set-traffic-light":
        client.set(KEY_TRAFFIC, traffic(color=COLOR[args.color])); print(f"traffic={args.color}")
    elif args.command == "set-obstacle":
        client.set(KEY_OBSTACLES, obstacles(kind=OBSTACLE_TYPE[args.type], x=args.x, y=args.y)); client.set(KEY_MAIN_OBSTACLE, main_obstacle(kind=OBSTACLE_TYPE[args.type], x=args.x, y=args.y)); print("updated obstacle")
    elif args.command == "set-warning":
        client.set(KEY_DCU_INFO_2, dcu_info_2(auto_limit=args.auto_limit, emergency=args.emergency, low_fault=args.low_fault, takeover=args.takeover, drive_mode=args.drive_mode, auto_out=args.auto_out, brake_fault=args.brake_fault, high_fault=args.high_fault)); print("updated DCU_INFO_2 warning state")
    elif args.command == "set-l2":
        client.set(KEY_L2, l2_state(acc_status=args.acc_status, acc_fail=args.acc_fail, acc_quit=args.acc_quit, lka_status=args.lka_status, lka_fail=args.lka_fail, lka_quit=args.lka_quit)); print("updated L2 state")
    elif args.command == "set-perception-fault":
        client.set(KEY_MAIN_OBSTACLE, main_obstacle(camera_fault=args.camera, radar_fault=args.radar, vehicle_fault=args.vehicle, fusion_fault=args.fusion)); print("updated perception fault state")
    elif args.command == "set-sam":
        client.set(KEY_SAM, sam(scene=args.scene, event=EVENT_TYPE[args.event], count=args.count, guide=args.guide, feedback=args.feedback, behavior=args.behavior)); print("updated Sam cooperative state")
    elif args.command == "corrupt":
        client.set(args.key, b"\x0d\x01"); print(f"corrupted {args.key}")
    elif args.command == "delete":
        client.delete(args.key); print(f"deleted {args.key}")
    elif args.command == "get":
        value = client.get(args.key); print("missing" if value is None else f"{args.key}: {len(value)} bytes hex={value[:32].hex()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
