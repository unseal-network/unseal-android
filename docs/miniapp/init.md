@features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/messagecomposer/AttachmentsBottomSheet.kt 添加一个游戏按钮

点击按钮，弹出界面界，面显示如下

数据接口
homesever 去代码里找

/**
 * 获取游戏列表
 * GET {homeserver}/app-mgr/package/json?method=pkg.app.list&page=1&size=10&classify=2
 */
export async function fetchAppList(page = 1, size = 10): Promise<GameInfo[]> {
    const classify = homesever.includes("keepsecret.io") ? 3 : 2;
    const url = `${homeserver}/app-mgr/room/api/json?method=pkg.app.list&page=${page}&size=${size}&classify=${classify}`;
    const res = await fetch(url, { headers: { "APP-U": getAppUHeader() } });
    const json = await res.json();
    if (json.code !== 0) throw new Error(`fetchAppList failed: ${JSON.stringify(json)}`);
    return (json.data as any[]).map((item) => ({
        id: item.id,
        name: item.name,
        brief: item.brief,
        icon: item.icon,
        remote_url: item.remote_url ?? null,
    }));
}

/**
 * 查询当前用户正在进行的游戏房间列表
 * GET {homeserver}/app-mgr/room/api/rooms/my-playing
 * 401 时自动刷新 token 重试，最多 3 次
 */

 function getAppUHeader(): string {
    return `s=${homeserver.replace(/^https?:\/\//, "")}`;
}
export async function fetchMyPlaying(page = 1, limit = 6): Promise<PlayingRoom[]> {
    const url = `${homeserver}/app-mgr/room/api/rooms/my-playing?page=${page}&limit=${limit}`;
    const MAX_RETRIES = 3;

    for (let attempt = 0; attempt < MAX_RETRIES; attempt++) {
        await ensureTokenValid();
        const token = MatrixClientPeg.safeGet().getAccessToken() ?? "";
        const res = await fetch(url, {
            headers: {
                "APP-U": getAppUHeader(),
                UnsealToken: token,
            },
        });

        if (res.status === 401) {
            if (attempt < MAX_RETRIES - 1) continue; // 刷新 token 后重试
            throw new Error(`fetchMyPlaying: unauthorized after ${MAX_RETRIES} attempts`);
        }

        const json = await res.json();
        if (!json.success) throw new Error(`fetchMyPlaying failed: ${JSON.stringify(json)}`);
        return (json.data?.rooms ?? []) as PlayingRoom[];
    }

    throw new Error("fetchMyPlaying: max retries exceeded");
}
游戏列表点击后发送消息

具体可以参考 @/Users/ranjun/Desktop/Works/me/document-demo/works/unseal/unseal-web/unseal-webapp/src/components/views/rooms/RoomHeader/GameButtons.tsx，先给我方案，方案文档放在 @docs/miniapp 下