"""把游戏合集整理成游戏包：zip 里每个分类一个文件夹，文件夹里是 jar。在 App 里选择这个 zip 即可导入。

用法: python tools/build_pack.py 输入(zip或目录)... -o 输出.zip [--exclude 已有游戏包.zip ...]

- 自动识别 GBK 编码的老压缩包文件名，嵌套 zip 也会读取
- 去掉坏文件、内容或名字重复的、已在 --exclude 游戏包里的；去掉"大屏通用版(240x320)"等名字后缀
- 输出结构: 分类/游戏名.jar（想调整分类，把 jar 挪到别的文件夹再压缩即可）
"""
import argparse
import hashlib
import io
import re
import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from prepare_games import classify, clean_name, read_midlet  # noqa: E402

FOLDER_CATEGORIES = {
    "动作": "动作射击", "射击": "动作射击", "策略": "策略经营", "赛车": "赛车体育", "体育": "赛车体育",
    "益智": "休闲益智", "休闲": "休闲益智", "棋牌": "棋牌", "rpg": "角色扮演", "角色扮演": "角色扮演",
}
SPECIFIC = {"动作射击", "策略经营", "赛车体育", "休闲益智", "棋牌"}


def decode_name(info: zipfile.ZipInfo) -> str:
    if info.flag_bits & 0x800:
        return info.filename
    try:
        raw = info.filename.encode("cp437")
    except UnicodeEncodeError:
        return info.filename
    for enc in ("utf-8", "gbk"):
        try:
            return raw.decode(enc)
        except UnicodeDecodeError:
            pass
    return info.filename


def norm(name: str) -> str:
    name = re.sub(r"\s*\(\d+\)$", "", name.strip().lower())
    return re.sub(r"\s+", "", name)


def category_for(name: str, path: str) -> str:
    folder = None
    for part in path.split("/")[:-1]:
        folder = FOLDER_CATEGORIES.get(part.strip().lower(), folder)
    if folder in SPECIFIC:
        return folder
    cat = classify(name)
    if cat == "其他" and folder:
        return folder
    return cat


def iter_jars(source: Path):
    """产出 (显示路径, jar 字节)"""
    if source.is_dir():
        for p in sorted(source.rglob("*")):
            if p.suffix.lower() == ".jar":
                yield str(p.relative_to(source)).replace("\\", "/"), p.read_bytes()
            elif p.suffix.lower() == ".zip":
                yield from iter_zip(zipfile.ZipFile(p), p.name + "/")
    else:
        yield from iter_zip(zipfile.ZipFile(source), "")


def iter_zip(z: zipfile.ZipFile, prefix: str):
    for info in z.infolist():
        if info.is_dir():
            continue
        path = prefix + decode_name(info)
        low = path.lower()
        if low.endswith(".jar"):
            yield path, z.read(info)
        elif low.endswith(".zip"):
            yield from iter_zip(zipfile.ZipFile(io.BytesIO(z.read(info))), path + "/")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("inputs", nargs="+", help="游戏合集 zip 或目录")
    parser.add_argument("-o", "--output", required=True, help="输出 zip 路径")
    parser.add_argument("--exclude", nargs="*", default=[], help="已有的游戏包，里面有的游戏不再重复打包")
    args = parser.parse_args()

    excluded = set()
    for pack in args.exclude:
        with zipfile.ZipFile(pack) as z:
            excluded |= {norm(Path(decode_name(i)).stem) for i in z.infolist() if i.filename.lower().endswith(".jar")}

    out_path = Path(args.output)
    check_file = out_path.with_suffix(".check.jar")
    seen_names, seen_hashes, used_paths = set(), set(), set()
    counts, stats = {}, {"total": 0, "kept": 0, "invalid": 0, "excluded": 0, "duplicate": 0}
    with zipfile.ZipFile(out_path, "w", zipfile.ZIP_STORED, allowZip64=True) as out:
        for source in args.inputs:
            for path, data in iter_jars(Path(source)):
                stats["total"] += 1
                if stats["total"] % 500 == 0:
                    print(f"  已处理 {stats['total']} 个…", flush=True)
                name = clean_name(Path(path).stem.strip())
                key = norm(name)
                digest = hashlib.md5(data).hexdigest()
                if key in excluded:
                    stats["excluded"] += 1
                    continue
                if key in seen_names or digest in seen_hashes:
                    stats["duplicate"] += 1
                    continue
                check_file.write_bytes(data)
                valid, _ = read_midlet(check_file)
                if not valid:
                    stats["invalid"] += 1
                    continue
                seen_names.add(key)
                seen_hashes.add(digest)
                category = category_for(name, path)
                safe = re.sub(r'[\\/:*?"<>|]', "_", name)
                entry, i = f"{category}/{safe}.jar", 2
                while entry.lower() in used_paths:
                    entry, i = f"{category}/{safe}_{i}.jar", i + 1
                used_paths.add(entry.lower())
                out.writestr(entry, data)
                counts[category] = counts.get(category, 0) + 1
                stats["kept"] += 1
    check_file.unlink(missing_ok=True)

    print(f"共扫描 {stats['total']} 个 jar：保留 {stats['kept']} 个，"
          f"已在其他游戏包 {stats['excluded']} 个，内容/同名重复 {stats['duplicate']} 个，坏文件 {stats['invalid']} 个")
    for cat, n in sorted(counts.items(), key=lambda x: -x[1]):
        print(f"  {cat}: {n}")
    print(f"输出: {out_path} ({out_path.stat().st_size / 1024 / 1024:.0f} MB)")


if __name__ == "__main__":
    main()
