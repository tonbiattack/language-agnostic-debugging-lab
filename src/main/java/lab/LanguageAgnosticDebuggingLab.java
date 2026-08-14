package lab;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.StringJoiner;

/**
 * A self-contained, language-agnostic debugging practice lab.
 *
 * <p>The program presents short scenarios, simulated observations and graduated hints.
 * Learners identify the fault category instead of merely copying a fixed answer. Progress
 * is stored locally in the user's home directory.</p>
 */
public final class LanguageAgnosticDebuggingLab {
    private LanguageAgnosticDebuggingLab() {
    }

    public static void main(String[] args) {
        String progressOverride = System.getenv("DEBUGGING_LAB_PROGRESS_FILE");
        Path progressFile = progressOverride == null || progressOverride.isBlank()
                ? Path.of(System.getProperty("user.home"), ".debugging-lab-progress.tsv")
                : Path.of(progressOverride);
        ConsoleApplication application = new ConsoleApplication(
                ScenarioCatalog.create(),
                new ProgressStore(progressFile),
                System.out,
                new Scanner(System.in, StandardCharsets.UTF_8));
        application.run(args);
    }
}

final class ConsoleApplication {
    private final List<BugScenario> scenarios;
    private final ProgressStore progressStore;
    private final PrintStream output;
    private final Scanner input;

    ConsoleApplication(List<BugScenario> scenarios, ProgressStore progressStore, PrintStream output, Scanner input) {
        this.scenarios = List.copyOf(scenarios);
        this.progressStore = progressStore;
        this.output = output;
        this.input = input;
    }

    void run(String[] args) {
        try {
            if (args.length == 0 || contains(args, "--interactive")) {
                printBanner();
                runInteractive();
                return;
            }
            runCommand(args);
        } catch (IllegalArgumentException exception) {
            output.println("エラー: " + exception.getMessage());
            output.println("\n--help で使い方を確認してください。");
        } catch (IOException exception) {
            output.println("進捗ファイルを更新できませんでした: " + exception.getMessage());
        }
    }

    private boolean contains(String[] args, String target) {
        for (String argument : args) {
            if (argument.equals(target)) {
                return true;
            }
        }
        return false;
    }

    private void runCommand(String[] args) throws IOException {
        String command = args[0];
        switch (command) {
            case "--help", "-h" -> printHelp();
            case "--list" -> printScenarioList();
            case "--progress" -> printProgress();
            case "--show" -> showScenario(requireScenarioId(args, command));
            case "--run" -> runDiagnostics(requireScenarioId(args, command));
            case "--hint" -> showHint(requireScenarioId(args, command), 0);
            case "--answer" -> submitCommandAnswer(args);
            default -> throw new IllegalArgumentException("未知のコマンドです: " + command);
        }
    }

    private String requireScenarioId(String[] args, String command) {
        if (args.length < 2 || args[1].isBlank()) {
            throw new IllegalArgumentException(command + " にはシナリオIDが必要です。");
        }
        return args[1];
    }

    private void submitCommandAnswer(String[] args) throws IOException {
        if (args.length < 3) {
            throw new IllegalArgumentException("--answer にはシナリオIDと診断内容が必要です。");
        }
        StringJoiner answer = new StringJoiner(" ");
        for (int index = 2; index < args.length; index++) {
            answer.add(args[index]);
        }
        submitAnswer(findScenario(args[1]), answer.toString(), 0);
    }

    private void printBanner() {
        output.println("============================================================");
        output.println("             Language-Agnostic Debugging Lab");
        output.println("       バグの症状から原因を切り分ける学習用CLI");
        output.println("============================================================");
        output.println("\nコマンドライン例: --list / --show N01 / --run N01 / --answer N01 null guard");
        output.println("対話モードでは、各シナリオの観察結果・ヒント・回答判定を利用できます。\n");
    }

    private void printHelp() {
        printBanner();
        output.println("使い方:");
        output.println("  ./scripts/run.sh --interactive              対話モードを開始します。");
        output.println("  ./scripts/run.sh --list                     シナリオ一覧を表示します。");
        output.println("  ./scripts/run.sh --show <ID>                問題文とコードを表示します。");
        output.println("  ./scripts/run.sh --run <ID>                 再現テストの観察結果を表示します。");
        output.println("  ./scripts/run.sh --hint <ID>                最初のヒントを表示します。");
        output.println("  ./scripts/run.sh --answer <ID> <診断>       診断を採点し、進捗を保存します。");
        output.println("  ./scripts/run.sh --progress                 保存済みの進捗を表示します。");
        output.println("\nIDは N01, B02, M03, R04, C05, T06 の形式です。");
    }

    private void runInteractive() throws IOException {
        while (true) {
            output.println("----------------------------------------");
            output.println("[L] 一覧  [S] 問題表示  [R] 再現テスト  [H] ヒント  [A] 回答  [P] 進捗  [Q] 終了");
            output.print("操作を選択してください: ");
            if (!input.hasNextLine()) {
                output.println("\n入力を終了しました。");
                return;
            }
            String selection = input.nextLine().trim().toUpperCase(Locale.ROOT);
            switch (selection) {
                case "L" -> printScenarioList();
                case "S" -> {
                    BugScenario scenario = promptScenario();
                    if (scenario != null) {
                        showScenario(scenario.id());
                    }
                }
                case "R" -> {
                    BugScenario scenario = promptScenario();
                    if (scenario != null) {
                        runDiagnostics(scenario.id());
                    }
                }
                case "H" -> {
                    BugScenario scenario = promptScenario();
                    if (scenario != null) {
                        int hintNumber = promptHintNumber(scenario);
                        showHint(scenario.id(), hintNumber);
                    }
                }
                case "A" -> {
                    BugScenario scenario = promptScenario();
                    if (scenario != null) {
                        output.print("原因の診断を入力してください: ");
                        String answer = input.hasNextLine() ? input.nextLine() : "";
                        int hintsUsed = promptHintsUsed();
                        submitAnswer(scenario, answer, hintsUsed);
                    }
                }
                case "P" -> printProgress();
                case "Q", "QUIT", "EXIT" -> {
                    output.println("学習を終了します。お疲れさまでした。");
                    return;
                }
                case "" -> output.println("操作を入力してください。");
                default -> output.println("L, S, R, H, A, P, Q のいずれかを入力してください。");
            }
        }
    }

    private BugScenario promptScenario() {
        output.print("シナリオIDを入力してください: ");
        if (!input.hasNextLine()) {
            return null;
        }
        String id = input.nextLine().trim();
        try {
            return findScenario(id);
        } catch (IllegalArgumentException exception) {
            output.println(exception.getMessage());
            return null;
        }
    }

    private int promptHintNumber(BugScenario scenario) {
        output.print("ヒント番号 (1-" + scenario.hints().size() + ", 省略時は1): ");
        if (!input.hasNextLine()) {
            return 0;
        }
        String answer = input.nextLine().trim();
        if (answer.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(answer) - 1;
        } catch (NumberFormatException exception) {
            output.println("番号として解釈できないため、最初のヒントを表示します。");
            return 0;
        }
    }

    private int promptHintsUsed() {
        output.print("参照したヒント数 (0-3, 省略時は0): ");
        if (!input.hasNextLine()) {
            return 0;
        }
        String answer = input.nextLine().trim();
        if (answer.isEmpty()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(answer));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private void printScenarioList() {
        Map<String, ProgressEntry> progress = progressStore.loadSafely();
        output.println("\n利用可能なシナリオ:");
        output.printf("%-4s %-26s %-28s %-12s %-8s%n", "ID", "テーマ", "主な言語", "難易度", "状態");
        output.println("---------------------------------------------------------------------------------------");
        for (BugScenario scenario : scenarios) {
            ProgressEntry entry = progress.get(scenario.id());
            String state = entry != null && entry.solved() ? "完了" : "未完了";
            output.printf("%-4s %-26s %-28s %-12s %-8s%n",
                    scenario.id(), scenario.title(), scenario.primaryLanguage(), scenario.difficulty().label(), state);
        }
        output.println();
    }

    private void showScenario(String id) {
        BugScenario scenario = findScenario(id);
        output.println("\n[" + scenario.id() + "] " + scenario.title() + " — " + scenario.difficulty().label());
        output.println("対象: " + scenario.primaryLanguage());
        output.println("症状: " + scenario.symptom());
        output.println("期待する動作: " + scenario.expectedBehavior());
        output.println("\n調査対象コード:");
        output.println("```" + scenario.primaryLanguage().toLowerCase(Locale.ROOT));
        output.println(scenario.code());
        output.println("```");
        output.println("\nまずは再現テストを実行し、症状を絞り込んでください: --run " + scenario.id());
    }

    private void runDiagnostics(String id) {
        BugScenario scenario = findScenario(id);
        DiagnosticReport report = DiagnosticSimulator.run(scenario);
        output.println("\n[" + scenario.id() + "] 再現テスト結果");
        output.println("------------------------------------------------------------");
        for (Observation observation : report.observations()) {
            String status = observation.passed() ? "PASS" : "FAIL";
            output.printf("%-5s %-18s %s%n", status, observation.caseName(), observation.detail());
        }
        output.printf("\n結果: %d 件成功 / %d 件失敗%n", report.passedCount(), report.failedCount());
        output.println("失敗パターンに共通する入力・実行条件から原因カテゴリを考えてください。");
    }

    private void showHint(String id, int hintIndex) {
        BugScenario scenario = findScenario(id);
        int boundedIndex = Math.max(0, Math.min(hintIndex, scenario.hints().size() - 1));
        output.println("\nヒント " + (boundedIndex + 1) + "/" + scenario.hints().size() + ": " + scenario.hints().get(boundedIndex));
    }

    private void submitAnswer(BugScenario scenario, String answer, int hintsUsed) throws IOException {
        AnswerEvaluation evaluation = AnswerEvaluator.evaluate(scenario, answer);
        int boundedHints = Math.max(0, Math.min(hintsUsed, scenario.hints().size()));
        int score = evaluation.correct() ? scenario.difficulty().points() - (boundedHints * 5) : 0;
        ProgressEntry progress = progressStore.recordAttempt(scenario.id(), evaluation.correct(), Math.max(0, score));

        if (evaluation.correct()) {
            output.println("\n正解です。" + evaluation.matchedConcept() + " を正しく特定できました。");
            output.println("原因箇所: " + scenario.faultLocation());
            output.println("解説: " + scenario.faultExplanation());
            output.println("今回の得点: " + score + " 点 / 累計: " + progress.totalPoints() + " 点");
        } else {
            output.println("\nまだ原因カテゴリを特定できていません。");
            output.println("再現テストの失敗条件を確認し、データ境界・型・共有状態・時刻基準を順に疑ってください。");
            output.println("試行回数: " + progress.attempts() + " 回。必要なら --hint " + scenario.id() + " を使えます。");
        }
    }

    private void printProgress() {
        Map<String, ProgressEntry> entries = progressStore.loadSafely();
        int completed = 0;
        int attempts = 0;
        int points = 0;
        for (ProgressEntry entry : entries.values()) {
            if (entry.solved()) {
                completed++;
            }
            attempts += entry.attempts();
            points += entry.totalPoints();
        }
        output.println("\n学習進捗");
        output.println("------------------------------------------------------------");
        output.println("完了シナリオ: " + completed + " / " + scenarios.size());
        output.println("総試行回数  : " + attempts);
        output.println("累計スコア  : " + points + " 点");
        if (completed == scenarios.size()) {
            output.println("すべて完了です。各シナリオの修正案を自分の言葉で説明してみましょう。");
        }
    }

    private BugScenario findScenario(String id) {
        return scenarios.stream()
                .filter(scenario -> scenario.id().equalsIgnoreCase(id.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("シナリオが見つかりません: " + id));
    }
}

record BugScenario(
        String id,
        String title,
        String primaryLanguage,
        Difficulty difficulty,
        String symptom,
        String expectedBehavior,
        String code,
        String faultLocation,
        String faultExplanation,
        List<String> hints,
        List<Observation> observations,
        List<String> acceptedKeywords) {

    BugScenario {
        if (id == null || id.isBlank() || title == null || title.isBlank()) {
            throw new IllegalArgumentException("シナリオにはIDとタイトルが必要です。");
        }
        hints = List.copyOf(hints);
        observations = List.copyOf(observations);
        acceptedKeywords = List.copyOf(acceptedKeywords);
    }
}

record Observation(String caseName, boolean passed, String detail) {
}

record DiagnosticReport(List<Observation> observations, int passedCount, int failedCount) {
}

record AnswerEvaluation(boolean correct, String matchedConcept) {
}

enum Difficulty {
    FOUNDATION("基礎", 30),
    PRACTICAL("実践", 50),
    ADVANCED("応用", 70);

    private final String label;
    private final int points;

    Difficulty(String label, int points) {
        this.label = label;
        this.points = points;
    }

    String label() {
        return label;
    }

    int points() {
        return points;
    }
}

final class DiagnosticSimulator {
    private DiagnosticSimulator() {
    }

    static DiagnosticReport run(BugScenario scenario) {
        int passed = 0;
        for (Observation observation : scenario.observations()) {
            if (observation.passed()) {
                passed++;
            }
        }
        return new DiagnosticReport(scenario.observations(), passed, scenario.observations().size() - passed);
    }
}

final class AnswerEvaluator {
    private AnswerEvaluator() {
    }

    static AnswerEvaluation evaluate(BugScenario scenario, String answer) {
        String normalized = answer == null ? "" : answer.toLowerCase(Locale.ROOT);
        for (String keyword : scenario.acceptedKeywords()) {
            if (normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                return new AnswerEvaluation(true, "「" + keyword + "」");
            }
        }
        return new AnswerEvaluation(false, "");
    }
}

record ProgressEntry(int attempts, boolean solved, int totalPoints, Instant lastAttemptAt) {
    ProgressEntry recordAttempt(boolean correct, int points) {
        return new ProgressEntry(
                attempts + 1,
                solved || correct,
                totalPoints + (correct && !solved ? points : 0),
                Instant.now());
    }
}

final class ProgressStore {
    private static final String HEADER = "id\tattempts\tsolved\tpoints\tlastAttemptAt";
    private final Path progressFile;

    ProgressStore(Path progressFile) {
        this.progressFile = progressFile;
    }

    Map<String, ProgressEntry> loadSafely() {
        try {
            return load();
        } catch (IOException exception) {
            return Map.of();
        }
    }

    ProgressEntry recordAttempt(String scenarioId, boolean correct, int points) throws IOException {
        Map<String, ProgressEntry> entries = load();
        ProgressEntry next = entries.getOrDefault(scenarioId, new ProgressEntry(0, false, 0, Instant.EPOCH))
                .recordAttempt(correct, points);
        entries.put(scenarioId, next);
        save(entries);
        return next;
    }

    Map<String, ProgressEntry> load() throws IOException {
        if (!Files.exists(progressFile)) {
            return new LinkedHashMap<>();
        }
        Map<String, ProgressEntry> entries = new LinkedHashMap<>();
        for (String line : Files.readAllLines(progressFile, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.equals(HEADER)) {
                continue;
            }
            String[] columns = line.split("\\t", -1);
            if (columns.length != 5) {
                continue;
            }
            try {
                entries.put(columns[0], new ProgressEntry(
                        Integer.parseInt(columns[1]),
                        Boolean.parseBoolean(columns[2]),
                        Integer.parseInt(columns[3]),
                        Instant.parse(columns[4])));
            } catch (RuntimeException ignored) {
                // A corrupt entry should not prevent the learner from continuing.
            }
        }
        return entries;
    }

    private void save(Map<String, ProgressEntry> entries) throws IOException {
        Path parent = Optional.ofNullable(progressFile.getParent()).orElse(Path.of("."));
        Files.createDirectories(parent);
        StringBuilder content = new StringBuilder(HEADER).append(System.lineSeparator());
        entries.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .forEach(entry -> {
                    ProgressEntry value = entry.getValue();
                    content.append(entry.getKey()).append('\t')
                            .append(value.attempts()).append('\t')
                            .append(value.solved()).append('\t')
                            .append(value.totalPoints()).append('\t')
                            .append(value.lastAttemptAt()).append(System.lineSeparator());
                });
        Path temporary = Files.createTempFile(parent, "debugging-lab-progress-", ".tmp");
        Files.writeString(temporary, content.toString(), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, progressFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, progressFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

final class ScenarioCatalog {
    private ScenarioCatalog() {
    }

    static List<BugScenario> create() {
        List<BugScenario> scenarios = new ArrayList<>();
        scenarios.add(new BugScenario(
                "N01",
                "空の顧客メモで処理が停止する",
                "Java",
                Difficulty.FOUNDATION,
                "一部の注文だけで NullPointerException が発生し、通知処理全体が停止する。",
                "メモが未入力の注文でも、空文字として正規化して後続の処理を継続する。",
                "String normalizeNote(String note) {\n"
                        + "    return note.trim().toLowerCase(Locale.ROOT);\n"
                        + "}",
                "normalizeNote の note.trim()",
                "未入力値が null になる経路を考慮せずにメソッドを呼び出しています。入力境界で null を空文字へ正規化するか、明示的に拒否してください。",
                List.of(
                        "失敗したケースと成功したケースで、note の値がどう違うか比べてください。",
                        "メソッド呼び出しの前に、参照そのものが存在するか検証する必要はありませんか。",
                        "null ガードまたは Optional を使い、未入力の意味を仕様として扱ってください。"),
                List.of(
                        new Observation("通常のメモ", true, "\"Thank you\" → \"thank you\""),
                        new Observation("空文字", true, "\"\" → \"\""),
                        new Observation("未入力", false, "null → NullPointerException at normalizeNote:2")),
                List.of("null", "nullable", "nullpointer", "null guard", "入力検証")));

        scenarios.add(new BugScenario(
                "B02",
                "最終要素だけが集計できない",
                "Java / C# / JavaScript",
                Difficulty.FOUNDATION,
                "リストを走査する集計処理が、空でない入力で最後に例外を出す。",
                "0件なら0、n件なら0番目からn-1番目までを一度ずつ集計する。",
                "int total(List<Integer> values) {\n"
                        + "    int sum = 0;\n"
                        + "    for (int index = 0; index <= values.size(); index++) {\n"
                        + "        sum += values.get(index);\n"
                        + "    }\n"
                        + "    return sum;\n"
                        + "}",
                "for 条件の index <= values.size()",
                "size は要素数であり、最後に有効な添字は size - 1 です。ループの終了条件が1回多く、境界外アクセスを引き起こしています。",
                List.of(
                        "失敗するのは先頭・中間・末尾のどの時点か確認してください。",
                        "添字が取り得る最大値と、size が表す意味を分けて考えてください。",
                        "0始まりの配列では、比較演算子を < にするのが典型的な修正です。"),
                List.of(
                        new Observation("空リスト", true, "[] → 0"),
                        new Observation("単一要素", false, "[7] → IndexOutOfBoundsException (index=1)"),
                        new Observation("3要素", false, "[2, 4, 6] → IndexOutOfBoundsException (index=3)")),
                List.of("off-by-one", "off by one", "境界", "<=", "indexoutofbounds")));

        scenarios.add(new BugScenario(
                "M03",
                "達成率が常に0%になる",
                "Java / Python / SQL",
                Difficulty.PRACTICAL,
                "完了件数が総件数より少ない案件で、ダッシュボードの達成率が0%と表示される。",
                "分子・分母に応じた小数の割合を計算し、表示直前にのみ丸める。",
                "int completionRate(int completed, int total) {\n"
                        + "    return (completed / total) * 100;\n"
                        + "}",
                "(completed / total) の整数除算",
                "両方が整数型のため、割り算の時点で小数部が切り捨てられます。演算前に少なくとも一方を浮動小数型へ変換し、total が0の場合も仕様化してください。",
                List.of(
                        "50%未満だけが0になるなら、演算順序より先に型を確認してください。",
                        "100を掛ける前の completed / total はどの型で評価されていますか。",
                        "(double) completed / total のように、除算より前に型を変換してください。"),
                List.of(
                        new Observation("0 / 10", true, "0%"),
                        new Observation("5 / 10", false, "期待: 50%, 実際: 0%"),
                        new Observation("10 / 10", true, "100%")),
                List.of("integer division", "整数除算", "double", "浮動小数", "型変換", "cast")));

        scenarios.add(new BugScenario(
                "R04",
                "日次処理の途中でファイルがロックされる",
                "Java",
                Difficulty.PRACTICAL,
                "CSVを集計した後、Windows環境で元ファイルを移動できないことがある。",
                "集計完了後には入力ストリームを必ず閉じ、ファイル操作が可能な状態に戻す。",
                "long countRows(Path path) throws IOException {\n"
                        + "    Stream<String> rows = Files.lines(path);\n"
                        + "    return rows.filter(line -> !line.isBlank()).count();\n"
                        + "}",
                "Files.lines(path) が返す Stream を閉じていない箇所",
                "Files.lines はファイル資源を保持するストリームを返します。終端操作を呼んでも自動で閉じる契約ではないため、try-with-resources でスコープを限定してください。",
                List.of(
                        "例外ではなく、処理後のファイル操作でだけ失敗していませんか。",
                        "外部資源を開くAPIが返すオブジェクトのclose要否を確認してください。",
                        "try (Stream<String> rows = Files.lines(path)) { ... } の形で閉じてください。"),
                List.of(
                        new Observation("初回集計", true, "有効な行数 42 を取得"),
                        new Observation("直後のリネーム", false, "AccessDeniedException: file is in use"),
                        new Observation("JVM終了後のリネーム", true, "成功（資源解放後）")),
                List.of("resource", "close", "try-with-resources", "try with resources", "stream", "リソース")));

        scenarios.add(new BugScenario(
                "C05",
                "在庫がまれにマイナスになる",
                "Java / Go / C++",
                Difficulty.ADVANCED,
                "高負荷時だけ、在庫を0未満にしないはずの予約処理で負値が記録される。",
                "複数の予約要求が同時に来ても、在庫確認と減算を不可分に実行する。",
                "boolean reserve(int requested) {\n"
                        + "    if (stock >= requested) {\n"
                        + "        stock = stock - requested;\n"
                        + "        return true;\n"
                        + "    }\n"
                        + "    return false;\n"
                        + "}",
                "stock の確認と更新の間に排他制御がない箇所",
                "判定と更新が別々の操作なので、複数スレッドが同じ在庫を同時に読み取れます。ロック、原子的な比較更新、またはトランザクションで一連の操作を保護してください。",
                List.of(
                        "同じ入力でも、単一実行と並列実行で結果が異なるか比べてください。",
                        "共有される stock を読む時点と書く時点は、他の要求から隔離されていますか。",
                        "synchronized、Lock、CAS、またはDBトランザクションで比較と更新を一体化してください。"),
                List.of(
                        new Observation("単一予約", true, "stock=3, requested=2 → stock=1"),
                        new Observation("逐次2予約", true, "stock=3, 2件×2 → 1件だけ成功"),
                        new Observation("並列2予約", false, "2件とも成功し、最終 stock=-1")),
                List.of("race", "race condition", "競合", "synchron", "lock", "atomic", "排他")));

        scenarios.add(new BugScenario(
                "T06",
                "月末だけ締め日がずれる",
                "Java / JavaScript / SQL",
                Difficulty.ADVANCED,
                "UTCで保存した注文が、利用者の地域によって前日または翌日の売上に集計される。",
                "保存時の絶対時刻と、集計に使う業務タイムゾーンの日付を明示的に区別する。",
                "LocalDate accountingDate(Instant orderedAt) {\n"
                        + "    return orderedAt.atZone(ZoneId.systemDefault()).toLocalDate();\n"
                        + "}",
                "ZoneId.systemDefault() に業務規則を委ねている箇所",
                "サーバーの既定タイムゾーンは業務上の締め時刻を表しません。集計用のZoneIdを設定として明示し、UTCとの変換境界をテストしてください。",
                List.of(
                        "同じInstantを異なるサーバー地域で実行したときの日付を比較してください。",
                        "systemDefault はデプロイ先の設定で変わります。業務の地域設定と同じでしょうか。",
                        "ZoneId.of(\"Asia/Tokyo\") のように、要件で決めたタイムゾーンを明示してください。"),
                List.of(
                        new Observation("UTC 12:00", true, "多くの地域で同じ業務日"),
                        new Observation("UTC 23:30", false, "サーバー地域により集計日が変わる"),
                        new Observation("明示的な業務ZoneId", true, "すべての環境で同じ集計日")),
                List.of("timezone", "time zone", "タイムゾーン", "utc", "systemdefault", "zoneid")));

        return List.copyOf(scenarios);
    }
}
