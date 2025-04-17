package de.data_experts.swe_bench_lite_tester;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

@SpringBootApplication
@RestController
@RequestMapping("/test")
public class SweBenchLiteTesterApplication {

    private static final Logger log = LoggerFactory.getLogger(SweBenchLiteTesterApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(SweBenchLiteTesterApplication.class, args);
    }

    @PostMapping
    public Map<String, Object> runTests(@RequestBody TestPayload payload) {
        String repoDir = payload.getRepoDir();
        List<String> failToPass = payload.getFAIL_TO_PASS();
        List<String> passToPass = payload.getPASS_TO_PASS();

        Map<String, Object> result = new HashMap<>();

        try {
            File dir = new File(repoDir);
            if (!dir.exists() || !dir.isDirectory()) {
                result.put("error", "Invalid directory: " + repoDir);
                return result;
            }

            //TEST
            /*File dummy = new File(dir, "DUMMY_PATCH_TRIGGER.py");
            if (!dummy.exists()) {
                dummy.createNewFile();

                // Add the file to git tracking so that it appears in the diff
                ProcessBuilder gitAdd = new ProcessBuilder("git", "add", dummy.getName());
                gitAdd.directory(dir);
                Process addProcess = gitAdd.start();
                int addExit = addProcess.waitFor();
                if (addExit != 0) {
                    System.out.println("git add failed for dummy patch trigger.");
                }
            }*/
            //TEST

            // Generate patch file from diff
            File patchFile = new File(dir, "patch.diff");
            if (patchFile.exists()) {
                if(!patchFile.delete()) {
                    result.put("error", "Cannot delete patch file: " + patchFile.getAbsolutePath());
                    return result;
                }
            }
            String commitHash = getGitCommitHash(dir);
            Process diffProcess = createDiff(commitHash, dir, patchFile);
            diffProcess.waitFor();

            // Build command for run_evaluation
            String instanceId = payload.getInstance_id();
            String repoName = extractRepoNameFromGit(dir);
            File taskFile = new File(dir, "task_file.json");
            writeTaskFile(taskFile, failToPass, passToPass, instanceId, repoName, patchFile, commitHash);
            Process process = runEvaluation(dir, instanceId);

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                log.info(line);
            }

            int exitCode = process.waitFor();
            result.put("exitCode", exitCode);

            // Read harness output JSON file
            String reportPath = String.format("logs/run_evaluation/%s/report.json", instanceId);
            File evaluationReport = new File(dir, reportPath);
            if (evaluationReport.exists()) {
                try {
                    String reportContent = Files.readString(evaluationReport.toPath());
                    result.put("evaluationReport", reportContent);
                } catch (Exception e) {
                    result.put("evaluationReport", "Failed to read report.json: " + e.getMessage());
                }
            } else {
                result.put("evaluationReport", "report.json not found.");
            }
        } catch (Exception e) {
            result.put("error", e.getMessage());
            log.error(e.getMessage(), e);
        }
        return result;
    }

    private static Process createDiff(String commitHash, File dir, File patchFile) throws IOException {
        ProcessBuilder diffBuilder = new ProcessBuilder("git", "diff", "-w", commitHash);
        diffBuilder.directory(dir);
        Process diffProcess = diffBuilder.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(diffProcess.getInputStream()));
             BufferedWriter writer = new BufferedWriter(new FileWriter(patchFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.newLine();
            }
        }
        return diffProcess;
    }

    private static Process runEvaluation(File dir, String instanceId) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("python3");
        command.add("-m");
        command.add("swebench.harness.run_evaluation");
        command.add("--predictions_path");
        command.add(new File(dir, "task_file.json").getAbsolutePath());
        command.add("--run_id");
        command.add(instanceId);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(dir);
        pb.redirectErrorStream(true);
        return pb.start();
    }

    private void writeTaskFile(File file, List<String> fail, List<String> pass, String instanceId, String repoName, File patchFile, String commitHash) throws Exception {
        String patchContent = Files.readString(patchFile.toPath());
        patchContent = patchContent.replace("\r", ""); // CRLF → LF!

        String json = "[\n{" +
                "\"instance_id\": \"" + instanceId + "\"," +
                "\"model_name_or_path\": \"" + repoName + "\",\n" +
                "\"model_patch\": " + JSONObject.quote(patchContent) + ",\n" +
                "\"repo\": \"" + repoName + "\",\n" +
                "\"commit\": \"" + commitHash + "\"," +
                "\"FAIL_TO_PASS\": " + toJsonArray(fail) + "," +
                "\"PASS_TO_PASS\": " + toJsonArray(pass) +
                "}\n]";
        Files.writeString(file.toPath(), json);
    }

    private String toJsonArray(List<String> list) {
        return list.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(",", "[", "]"));
    }

    private String extractRepoNameFromGit(File repoDir) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("git", "remote", "get-url", "origin");
        pb.directory(repoDir);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String url = reader.readLine();
            process.waitFor();
            if (url == null || url.isEmpty()) {
                throw new Exception("Could not read git remote URL.");
            }
            url = url.replace(".git", "");
            if (url.startsWith("git@")) {
                url = url.replace("git@", "").replace(":", "/");
            } else if (url.startsWith("https://")) {
                url = url.replace("https://github.com/", "");
            }
            return url.trim();
        }
    }

    private String getGitCommitHash(File repoDir) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("git", "rev-parse", "HEAD");
        pb.directory(repoDir);
        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            return reader.readLine();
        }
    }

}
