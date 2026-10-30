"""打包 J2ME 游戏。

内置进 APK:   python tools/prepare_games.py [游戏目录]
生成游戏源:   python tools/prepare_games.py 游戏目录 --catalog 输出目录 --name "源名称"
"""
import argparse
import hashlib
import json
import re
import shutil
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_SRC = ROOT.parent / "java游戏合集" / "JAVA游戏" / "JAVA中文游戏"
OUT_DIR = ROOT / "app" / "src" / "main" / "assets" / "games"

# 按顺序匹配，先命中的分类优先
CATEGORIES = [
    ("棋牌", ["象棋", "麻将", "斗地主", "扑克", "牌", "拖拉机", "跑得快", "大富翁", "21点", "雀", "三缺一",
            "将军抽车", "围棋", "五子棋", "军棋", "跳棋", "老虎机", "德州"]),
    ("赛车体育", ["赛车", "飞车", "摩托", "狂飙", "漂移", "卡丁车", "足球", "篮球", "投篮", "网球", "台球", "板球",
              "保龄球", "高尔夫", "滑板", "滑雪", "世界杯", "奥运", "体育", "竞技", "拳击", "斯诺克", "排球",
              "棒球", "fifa", "nba", "asphalt", "极品飞车"]),
    ("休闲益智", ["贪吃蛇", "矿工", "挖金", "泡泡", "paopao", "祖玛", "方块", "砖块", "连连看", "大鱼吃小鱼",
              "100层", "一百层", "猫和老鼠", "老鼠", "玛莉", "玛利", "过山车", "ball", "宝石", "捉雨",
              "移动", "阿猫阿狗", "海底大冒险", "摩天楼", "archer", "命运之轮", "拼图", "找茬", "消除", "算术",
              "智力", "数字", "迷宫", "弹球", "捕鱼", "钓鱼", "水果", "小鸟", "宝宝", "时装", "劲乐团", "节奏",
              "炸弹", "推箱子", "华容道", "数独", "愤怒", "瘦身", "tetris", "bejeweled"]),
    ("三国历史", ["三国", "秦", "汉", "唐", "隋", "楚", "春秋", "战国", "水浒", "曹操", "吕布", "刘备", "岳飞",
              "霸王", "虞姬", "锦衣卫", "满江红", "花木兰", "贵妃", "杨戬", "狄仁杰", "林冲", "六郎", "封神"]),
    ("西幻冒险", ["暗黑", "龙骑", "诸神", "圣剑", "圣殿", "圣战", "圣光", "圣教", "魔兽", "天使", "末日", "守护者",
              "龙世界", "龙之", "最终幻想", "纳尼亚", "刺客", "西风狂诗曲", "英雄同盟", "英雄联盟", "诺亚",
              "魔之信条", "女巫", "炼狱", "死魂", "黑暗", "幻想之空", "零式", "龙岛", "darkest", "2012"]),
    ("动作射击", ["反恐", "雷电", "空战", "战机", "射击", "狙击", "特种", "特警", "魂斗罗", "合金弹头", "格斗",
              "拳王", "街霸", "生化", "僵尸", "坦克", "战士", "机甲", "忍者", "突击", "火线", "冲锋", "星际",
              "太空", "入侵", "军魂", "特工", "谍", "007", "越狱", "枪", "黑衣人", "变形", "斗魂", "咏春", "洪拳"]),
    ("策略经营", ["塔防", "防御", "防线", "文明", "经理", "经营", "牧场", "农场", "模拟", "人生", "大亨",
              "养成", "宠物", "部落", "战争艺术", "大航海", "海盗", "医院", "诊所", "比萨", "餐厅", "开发商"]),
    ("仙侠修真", ["仙", "修", "神", "魔", "妖", "天劫", "诛", "轩辕", "昆仑", "蜀山", "九天", "七界", "苍", "星辰",
              "上古", "轮回", "灵", "西游", "八仙", "济公", "哪吒", "降魔", "伏魔", "弑", "天", "异世", "龙", "佛",
              "白蛇", "吕洞宾", "盘龙"]),
    ("武侠江湖", ["剑", "侠", "江湖", "武林", "刀", "倚天", "七侠五义", "名捕", "神捕", "双骄", "陆小凤", "东方不败",
              "少林", "豪杰", "绝代", "江南", "血", "杀破狼", "复仇", "乌龙", "弯刃", "风"]),
]


NOISE = [
    r"[_\-\s]*大屏通用版", r"[_\-\s]*\(\d+[x×*]\d+\)", r"[,，.。_\s]*宝软网.*$", r"[,，.。_\s]*baoruan.*$",
]


def clean_name(name: str) -> str:
    for pattern in NOISE:
        name = re.sub(pattern, "", name, flags=re.I)
    return name.strip(" _-") or name


def classify(name: str) -> str:
    low = re.sub(r"汉化版?|中文版|简体版|繁体版", "", name.lower())
    for cat, words in CATEGORIES:
        if any(w in low for w in words):
            return cat
    return "其他"


PNG_MAGIC = b"\x89PNG\r\n\x1a\n"


def read_midlet(path: Path):
    """返回 (是否有效 MIDlet, 图标 PNG 字节或 None)"""
    try:
        with zipfile.ZipFile(path) as z:
            mf = z.read("META-INF/MANIFEST.MF").decode("utf-8", "ignore")
            if re.search(r"^MIDlet-1\s*:", mf, re.M | re.I) is None:
                return False, None
            m = re.search(r"^MIDlet-Icon\s*:\s*(.+)$", mf, re.M | re.I) \
                or re.search(r"^MIDlet-1\s*:\s*[^,]*,\s*([^,]+),", mf, re.M | re.I)
            if m:
                try:
                    data = z.read(m.group(1).strip().lstrip("/"))
                    if data.startswith(PNG_MAGIC):
                        return True, data
                except KeyError:
                    pass
            return True, None
    except Exception:
        return False, None


def main():
    parser = argparse.ArgumentParser(description="打包 J2ME 游戏：内置进 APK，或生成可在网上发布的游戏源")
    parser.add_argument("src", nargs="?", default=str(DEFAULT_SRC), help="jar 游戏所在目录")
    parser.add_argument("--catalog", metavar="OUT_DIR",
                        help="生成游戏源目录（catalog.json + games/ + icons/），上传到任意静态网站后在 App 里添加 catalog.json 的网址")
    parser.add_argument("--name", default="分享的游戏", help="游戏源名称（仅 --catalog 模式）")
    args = parser.parse_args()

    src = Path(args.src)
    out = Path(args.catalog) if args.catalog else OUT_DIR
    jar_dir = out / "games" if args.catalog else out
    jars = sorted(p for p in src.iterdir() if p.suffix.lower() == ".jar")
    if out.exists():
        shutil.rmtree(out)
    (out / "icons").mkdir(parents=True)
    jar_dir.mkdir(parents=True, exist_ok=True)

    games, skipped = [], []
    for jar in jars:
        valid, icon = read_midlet(jar)
        if not valid:
            skipped.append(jar.name)
            continue
        name = jar.stem.strip()
        gid = hashlib.md5(name.encode("utf-8")).hexdigest()[:12]
        shutil.copyfile(jar, jar_dir / f"{gid}.jar")
        if icon:
            (out / "icons" / f"{gid}.png").write_bytes(icon)
        if args.catalog:
            game = {"id": gid, "name": name, "category": classify(name), "url": f"games/{gid}.jar",
                    "size": jar.stat().st_size}
            if icon:
                game["icon"] = f"icons/{gid}.png"
        else:
            game = {"id": gid, "name": name, "category": classify(name), "icon": icon is not None}
        games.append(game)

    games.sort(key=lambda g: g["name"])
    if args.catalog:
        catalog = {"name": args.name, "version": 1, "games": games}
        (out / "catalog.json").write_text(json.dumps(catalog, ensure_ascii=False, indent=1), encoding="utf-8")
    else:
        (out / "games.json").write_text(json.dumps(games, ensure_ascii=False, indent=1), encoding="utf-8")

    counts = {}
    for g in games:
        counts[g["category"]] = counts.get(g["category"], 0) + 1
    print(f"已导入 {len(games)} 个游戏（{sum(1 for g in games if g.get('icon'))} 个有图标） -> {out}")
    for cat, n in sorted(counts.items(), key=lambda x: -x[1]):
        print(f"  {cat}: {n}")
    if skipped:
        print(f"跳过 {len(skipped)} 个无效 jar: {', '.join(skipped)}")


if __name__ == "__main__":
    main()
