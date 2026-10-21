# 评测工具

## OverlapChunkProbe.java（只切分，不消耗任何 API 额度）

用途：验证 `overlap` 参数在**生产切分器**（`DocumentSplitterFactory`）上是否真的改变切分结果，
以及改变多少——这是 T6-04「overlap 三组对照」里切分侧的证据。

跑法（Windows / Git Bash）：

```bash
cd E:/reg-project/kb-eval
JH="C:/Program Files/Microsoft/jdk-17.0.20.101-hotspot"
CP="e:/reg-project/aideepin/server/adi-common/target/classes;$(cat cp.txt)"
OUT=$(cygpath -w /tmp/ovprobe)     # 输出目录
mkdir -p /tmp/ovprobe
"$JH/bin/javac.exe" -encoding UTF-8 -cp "$CP" -d /tmp/ovprobe \
  e:/reg-project/aideepin/server/dev-scripts/eval/tools/OverlapChunkProbe.java
"$JH/bin/java.exe" -Dfile.encoding=UTF-8 -cp "$OUT;$CP" OverlapChunkProbe
```

输出：全语料（`docs/cn` 37 篇）在 `sentence_cn` / `recursive` 两种策略下，
overlap = 0 / 51 / 102 / 128 的段数、avgChar、avgToken、总字符、总 token。

结论见 `../reports/T6-04-overlap-实测结论.md`：
**overlap 生效但只在「单句超长」时触发，全语料只带来 0.33%~1.6% 字符重复，段数几乎不变。**
