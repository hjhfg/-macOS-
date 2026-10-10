#!/usr/bin/env python3
"""
把 ios.25pan.com.zip（TabOS 前端静态导出）转换为 Android 启动器需要的资源：

  1. app/src/main/assets/desktop_seed.json   桌面布局（页面 / Dock / 文件夹 / 书签）
  2. app/src/main/res/drawable/*.png          用到的图标与壁纸（文件名已规范化）

网页里的"系统应用"（电话、邮箱、相机……）在 Android 上没有固定包名，
因此这里只记录 Intent 角色（role:xxx），运行时由 PackageManager 解析真实应用。

用法：
    python3 android/tools/convert_web_to_android.py ios.25pan.com.zip
"""
from __future__ import annotations

import argparse
import json
import re
import shutil
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]          # .../android
ASSETS = ROOT / "app/src/main/assets"
DRAWABLE = ROOT / "app/src/main/res/drawable"

# 网页系统应用 id -> (Intent 角色, 中文名)。没有对应 Android 能力的（天气、视频、钱包、
# AI 助手、锁屏、Office、废纸篓等）不映射，转换时跳过。
SYSTEM_ROLES: dict[str, tuple[str, str]] = {
    "app-1": ("phone", "电话"),
    "app-2": ("camera", "相机"),
    "app-3": ("music", "音乐"),
    "app-4": ("settings", "设置"),
    "app-5": ("gallery", "照片"),
    "app-6": ("mail", "邮箱"),
    "app-7": ("calendar", "日历"),
    "app-8": ("maps", "地图"),
    "app-9": ("messages", "信息"),
    "app-10": ("browser", "浏览器"),
    "app-11": ("market", "应用商店"),
    "app-12": ("contacts", "通讯录"),
    "app-13": ("files", "文件"),
    "app-17": ("calculator", "计算器"),
    "app-19": ("recorder", "语音备忘录"),
    "app-24": ("wallpaper", "墙纸"),
}

DOCK_EXTRA_ROLE_SKIP = {"trash", "app-23"}  # 废纸篓、锁屏：Android 无对应能力


def is_image(path: Path) -> bool:
    """按魔数判断是不是真图片。

    源站导出里存在"扩展名是 .png、内容其实是 HTML"的文件（抓图标时拿到的 404 页面）。
    这种文件混进 res/drawable 后，构建期 aapt2 只是警告并原样拷贝，
    到了运行时解码失败就会崩 —— 所以必须在转换阶段拦掉。
    """
    with path.open("rb") as f:
        head = f.read(16)
    if head.startswith(b"\x89PNG\r\n\x1a\n") or head.startswith(b"\xff\xd8\xff") or head.startswith(b"GIF8"):
        return True
    if head[:4] == b"RIFF" and head[8:12] == b"WEBP":
        return True
    if head.lstrip().startswith((b"<svg", b"<?xml")):
        return True
    return False


def slug(name: str) -> str:
    """资源文件名：小写 + 只保留 [a-z0-9_]，以字母开头。"""
    base = re.sub(r"[^a-z0-9_]", "_", Path(name).stem.lower())
    base = re.sub(r"_+", "_", base).strip("_")
    return base if base[0].isalpha() else f"i_{base}"


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))["data"]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("zip", type=Path)
    args = ap.parse_args()

    tmp = Path(tempfile.mkdtemp(prefix="ios25pan_"))
    with zipfile.ZipFile(args.zip) as z:
        z.extractall(tmp, members=[m for m in z.namelist() if not m.startswith("__MACOSX")])
    site = next(tmp.glob("*/"))  # ios.25pan.com/
    api = site / "api/v1"

    layout = load_json(api / "desktop/public-config/index.html")
    dock_cfg = load_json(api / "admin/dock/default/index.html")
    icon_by_id = {}
    for page in layout["pages"]["data"]:
        for app in page:
            if app.get("icon"):
                icon_by_id[app["id"]] = app["icon"]
    folders = {fid: f for fid, f in layout["folders"]["folders"]}

    used_icons: set[str] = set()

    def icon_ref(web_path: str | None) -> str | None:
        """只接受包内自带的图标（/icons/...、/wallpaper/...），远程上传图标无法离线使用。"""
        if not web_path or not web_path.startswith("/icons/"):
            return None
        src = site / web_path.lstrip("/")
        if not src.exists():
            return None
        name = slug(src.name)
        used_icons.add(str(src))
        return name

    items: list[dict] = []
    skipped: list[str] = []
    order = 0

    def add_role(zone, page, rect, app_id, parent=None, dock_order=0):
        nonlocal order
        if app_id not in SYSTEM_ROLES:
            skipped.append(app_id)
            return
        role, title = SYSTEM_ROLES[app_id]
        order += 1
        items.append({
            "id": app_id, "zone": zone, "page": page, "parentId": parent,
            "type": "APP", "title": title, "action": f"role:{role}",
            "icon": icon_ref(icon_by_id.get(app_id)),
            "row": rect["row"], "col": rect["col"], "rowSpan": 1, "colSpan": 1,
            "order": dock_order or order,
        })

    def add_bookmark(zone, page, rect, app, parent=None, row_order=0):
        nonlocal order
        order += 1
        items.append({
            "id": app["id"], "zone": zone, "page": page, "parentId": parent,
            "type": "BOOKMARK", "title": app.get("name", ""),
            "action": f"url:{app['url']}", "icon": None,
            "row": rect["row"], "col": rect["col"], "rowSpan": 1, "colSpan": 1,
            "order": row_order or order,
        })

    # 网页里的 2x2 动态时钟卡片 -> Android 内置时钟小组件（占 2x2 格子）
    CLOCK_WIDGET_ID = "app-14"

    # ---- 桌面页 ----
    for page in layout["desk_layout"]["pages"]:
        pidx = page["index"]
        for it in page["items"]:
            rect = it["rect"]
            if it["kind"] == "folder":
                f = folders.get(it["appId"])
                if not f:
                    continue
                order += 1
                items.append({
                    "id": it["appId"], "zone": "page", "page": pidx, "parentId": None,
                    "type": "FOLDER", "title": f["name"], "action": f"folder:{it['appId']}",
                    "icon": None, "row": rect["row"], "col": rect["col"],
                    "rowSpan": 1, "colSpan": 1, "order": order,
                })
                n = 0
                for fp in f["pages"]:
                    for app in fp:
                        if app.get("url"):
                            n += 1
                            add_bookmark("folder", 0, {"row": n // 3, "col": n % 3},
                                         app, parent=it["appId"], row_order=n)
            elif it.get("app"):           # 网页书签（桌面上直接放的站点）
                add_bookmark("page", pidx, rect, it["app"])
            elif it["appId"] == CLOCK_WIDGET_ID:
                order += 1
                items.append({
                    "id": it["appId"], "zone": "page", "page": pidx, "parentId": None,
                    "type": "WIDGET", "title": "时钟", "action": "builtin:clock",
                    "icon": None, "row": rect["row"], "col": rect["col"],
                    "rowSpan": 2, "colSpan": 2, "order": order,
                })
            else:                         # 系统应用
                add_role("page", pidx, rect, it["appId"])

    # ---- Dock ----
    dock = []
    for a in dock_cfg["apps"]:
        if a.get("type") == "divider":
            dock.append({"id": "dock:" + a["id"], "zone": "dock", "page": 0, "parentId": None,
                         "type": "DIVIDER", "title": "", "action": "none", "icon": None,
                         "row": 0, "col": 0, "rowSpan": 1, "colSpan": 1, "order": a["order"]})
            continue
        if a["id"] in DOCK_EXTRA_ROLE_SKIP or a["id"] not in SYSTEM_ROLES:
            skipped.append("dock:" + a["id"])
            continue
        role, title = SYSTEM_ROLES[a["id"]]
        dock.append({"id": "dock:" + a["id"], "zone": "dock", "page": 0, "parentId": None,
                     "type": "APP", "title": title, "action": f"role:{role}",
                     "icon": icon_ref(a.get("icon")), "row": 0, "col": 0,
                     "rowSpan": 1, "colSpan": 1, "order": a["order"]})
    items.extend(dock)

    # 只保留 Android 端用得上的文件
    ASSETS.mkdir(parents=True, exist_ok=True)
    seed = {
        "version": 1,
        "sourceSite": "ios.25pan.com",
        "gridCols": layout["desk_layout"]["gridCols"],
        "pages": [{"index": p["index"], "title": p["title"]} for p in layout["desk_layout"]["pages"]],
        "items": items,
    }
    (ASSETS / "desktop_seed.json").write_text(
        json.dumps(seed, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    # 图标与壁纸
    DRAWABLE.mkdir(parents=True, exist_ok=True)
    copied = []
    bad_images: list[str] = []
    for src in sorted(used_icons):
        if not is_image(Path(src)):
            # 扩展名是图片、内容不是（多为源站的 404 页面），不能放进 res/
            bad_images.append(Path(src).name)
            continue
        dst = DRAWABLE / (slug(src) + ".png")
        shutil.copyfile(src, dst)
        copied.append(dst.name)
    for wp in (site / "backgrounds").glob("*.png"):
        dst = DRAWABLE / ("wallpaper_" + slug(wp.name) + ".png")
        shutil.copyfile(wp, dst)
        copied.append(dst.name)
    for wp in (site / "wallpaper").glob("*"):
        dst = DRAWABLE / ("wallpaper_" + slug(wp.name) + ".png")
        shutil.copyfile(wp, dst)
        copied.append(dst.name)

    shutil.rmtree(tmp, ignore_errors=True)
    print(f"seed items: {len(items)}  (pages {len(seed['pages'])}, dock {len(dock)})")
    print(f"drawables copied: {len(copied)}")
    if bad_images:
        print("skipped (不是真图片，源站多为 404 页面):", ", ".join(bad_images))
    print("skipped (no Android equivalent):", ", ".join(sorted(set(skipped))))


if __name__ == "__main__":
    main()
