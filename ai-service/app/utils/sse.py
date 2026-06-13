"""SSE 流式工具。"""
import asyncio
from typing import AsyncIterator


async def with_keepalive(source: AsyncIterator[str], interval: float = 15.0) -> AsyncIterator[str]:
    """包装 SSE 帧生成器：底层空闲超过 interval 秒时插入一帧 SSE 注释（``: keepalive``）。

    用途：agent 等长流在「等待 LLM / 工具调用」期间可能数十秒无业务帧，
    反向代理（nginx proxy_read_timeout）或中间层会因连接空闲而断开。注释帧以 ``:`` 开头，
    EventSource 与前端行解析器都会忽略，不影响业务帧，仅用于维持连接活跃。

    实现：用 shield 保护底层 __anext__ 任务不被 wait_for 超时取消；
    超时则发一帧 keepalive 后继续等待同一个任务。
    """
    ait = source.__aiter__()
    while True:
        task = asyncio.ensure_future(ait.__anext__())
        while True:
            try:
                item = await asyncio.wait_for(asyncio.shield(task), interval)
            except asyncio.TimeoutError:
                yield ": keepalive\n\n"
                continue
            except StopAsyncIteration:
                return
            break
        yield item
