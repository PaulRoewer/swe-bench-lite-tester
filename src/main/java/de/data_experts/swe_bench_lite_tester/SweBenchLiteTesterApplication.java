package de.data_experts.swe_bench_lite_tester;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

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
            Optional<File> reportFile = findReportJson(dir, instanceId);
            reportFile.ifPresent(file -> {
                try {
                    String content = Files.readString(file.toPath());
                    result.put("harnessOutput", content);
                } catch (IOException e) {
                    result.put("harnessOutput", "Failed to read output JSON: " + e.getMessage());
                }
            });
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
        command.add("--report_dir");
        command.add(new File(dir, "logs").getAbsolutePath());

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(dir);
        pb.redirectErrorStream(true);
        return pb.start();
    }

    private void writeTaskFile(File file, List<String> fail, List<String> pass, String instanceId, String repoName, File patchFile, String commitHash) throws Exception {
        String patchContent = Files.readString(patchFile.toPath());
        patchContent = patchContent.replace("\r", ""); // CRLF → LF!

        JSONArray failArray = new JSONArray(fail == null ? List.of() : fail);
        JSONArray passArray = new JSONArray(pass == null ? List.of() : pass);

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("instance_id", instanceId);
        jsonObject.put("model_name_or_path", repoName);
        jsonObject.put("model_patch", patchContent);  // keine quote() nötig
        jsonObject.put("repo", repoName);
        jsonObject.put("commit", commitHash);
        jsonObject.put("FAIL_TO_PASS", failArray);
        jsonObject.put("PASS_TO_PASS", passArray);

        JSONArray array = new JSONArray();
        array.put(jsonObject);

        Files.writeString(file.toPath(), array.toString(2));
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

    private Optional<File> findReportJson(File baseDir, String runId) {
        File logsRoot = new File(baseDir, "logs/run_evaluation");
        if (!logsRoot.exists()) {
            return Optional.empty();
        }

        try {
            return Files.walk(logsRoot.toPath())
                    .map(Path::toFile)
                    .filter(file -> file.isFile() && file.getName().equals("report.json"))
                    .filter(file -> file.getAbsolutePath().contains(runId)) // optional absichern
                    .findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }


}
