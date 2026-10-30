"""把 MRP 合集整理成游戏包：去掉坏文件和重复的，用 mrp 文件头里的中文名命名，按分类放文件夹。

用法: python tools/build_mrp_pack.py 输入zip或目录... -o 输出.zip
"""
import argparse
import hashlib
import re
import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_pack import decode_name, iter_jars, norm  # noqa: E402
from prepare_games import classify, clean_name  # noqa: E402

APP_KEYWORDS = re.compile(
    r"QQ|浏览器|browser|阅读器|音乐客户端|播放器|小说|天气|地图|输入法|聊天|飞信|助手|工具|电子书|商城|管理器|壁纸|"
    r"主题|闹钟|日历|计算器|下载|搜索|微博|邮箱|股票|新闻|视频|相册|短信|通讯录|字典|词典|翻译|导航|彩票|应用宝盒|"
    r"桌面|社区|安装|付费|MSN|ucweb|opera|reader|player|chat", re.I)


def mrp_title(data: bytes):
    if len(data) < 0x34 or data[:4] != b"MRPG":
        return None
    raw = data[0x1C:0x34].split(b"\0")[0]
    title = re.sub(r"[\x00-\x1f\ufffd]", "", raw.decode("gbk", "replace")).strip()
    return clean_name(title) if title else ""


def iter_mrps(source: Path):
    if source.is_dir():
        for p in sorted(source.rglob("*.mrp")) + sorted(source.rglob("*.MRP")):
            yield p.name, p.read_bytes()
        for p in sorted(source.rglob("*.zip")):
            yield from iter_zip(zipfile.ZipFile(p))
    else:
        yield from iter_zip(zipfile.ZipFile(source))


def iter_zip(z: zipfile.ZipFile):
    for info in z.infolist():
        if not info.is_dir() and info.filename.lower().endswith(".mrp"):
            yield decode_name(info), z.read(info)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("inputs", nargs="+")
    parser.add_argument("-o", "--output", required=True)
    args = parser.parse_args()

    candidates = []
    stats = {"total": 0, "invalid": 0, "duplicate": 0}
    for source in args.inputs:
        for path, data in iter_mrps(Path(source)):
            stats["total"] += 1
            title = mrp_title(data)
            if title is None:
                stats["invalid"] += 1
                continue
            stem = clean_name(Path(path).stem)
            # "xxx(1).mrp" 这类是重复拷贝，排在后面
            copy_rank = 1 if re.search(r"\(\d+\)\s*$", stem) else 0
            candidates.append((copy_rank, -len(data), title or stem, path, data))

    candidates.sort(key=lambda c: (c[0], c[1]))
    seen_names, seen_hashes, used = set(), set(), set()
    counts = {}
    out_path = Path(args.output)
    with zipfile.ZipFile(out_path, "w", zipfile.ZIP_STORED) as out:
        for _, _, name, path, data in candidates:
            digest = hashlib.md5(data).hexdigest()
            key = norm(name)
            if key in seen_names or digest in seen_hashes:
                stats["duplicate"] += 1
                continue
            seen_names.add(key)
            seen_hashes.add(digest)
            category = "应用工具" if APP_KEYWORDS.search(name) else classify(name)
            safe = re.sub(r'[\\/:*?"<>|]', "_", name)
            entry, i = f"{category}/{safe}.mrp", 2
            while entry.lower() in used:
                entry, i = f"{category}/{safe}_{i}.mrp", i + 1
            used.add(entry.lower())
            out.writestr(entry, data)
            counts[category] = counts.get(category, 0) + 1

    kept = sum(counts.values())
    print(f"共 {stats['total']} 个 mrp：保留 {kept} 个，重复 {stats['duplicate']} 个，坏文件 {stats['invalid']} 个")
    for cat, n in sorted(counts.items(), key=lambda x: -x[1]):
        print(f"  {cat}: {n}")
    print(f"输出: {out_path} ({out_path.stat().st_size / 1024 / 1024:.0f} MB)")


if __name__ == "__main__":
    main()
