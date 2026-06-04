import json
import redis
from typing import Any, Optional
from app.config.settings import settings

_client: Optional[redis.Redis] = None


def get_redis() -> redis.Redis:
    global _client
    if _client is None:
        _client = redis.Redis(
            host=settings.redis_host,
            port=settings.redis_port,
            password=settings.redis_password or None,
            db=settings.redis_db,
            decode_responses=True,
        )
    return _client


def set_json(key: str, value: Any, ttl: Optional[int] = None) -> None:
    r = get_redis()
    serialized = json.dumps(value, ensure_ascii=False)
    if ttl:
        r.setex(key, ttl, serialized)
    else:
        r.set(key, serialized)


def get_json(key: str) -> Optional[Any]:
    r = get_redis()
    raw = r.get(key)
    return json.loads(raw) if raw else None


def delete(key: str) -> None:
    get_redis().delete(key)
