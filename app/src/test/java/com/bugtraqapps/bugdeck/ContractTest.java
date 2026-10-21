package com.bugtraqapps.bugdeck;

import com.bugtraqapps.bugdeck.core.Fetcher;

/**
 * Contract test between the Android app's client and the bugXhunter server.
 *
 * <p>This is the real {@link UnifiedClient}, not a re-implementation, so a
 * change to either side that breaks the contract fails here.
 *
 * <p>The checks look at the raw response bodies rather than parsing them.
 * The thing under test is the wire contract between the two sides; how
 * the body is parsed is org.json's business, and the compile-only stubs
 * used for the local typecheck do not implement a parser.
 *
 * <p>Usage: ContractTest &lt;host&gt; &lt;token&gt;
 */
public final class ContractTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("usage: ContractTest <host> <token>");
            System.exit(2);
        }
        String host = args[0];
        String token = args[1];
        UnifiedClient client = new UnifiedClient(host, token, 10000);

        // 1. The link check.
        check("health reports the server is up", client.health());

        // 2. Device list.
        String devices = client.fridaDevicesJson();
        check("fridaDevicesJson returns a body", devices != null);
        check("device list carries a devices key",
                devices != null && devices.contains("\"devices\""));

        // 3. Process list, with and without a device.
        String procs = client.fridaProcessesJson("local");
        check("fridaProcessesJson(device) returns a body", procs != null);
        check("process list echoes the device",
                procs != null && procs.contains("\"device\""));
        check("process list carries a processes key",
                procs != null && procs.contains("\"processes\""));

        // 4. Script execution, as source text, with the "process" field
        //    name the app uses.
        String exec = client.fridaExecuteScript("console.log('contract-test');",
                "com.example.app");
        check("fridaExecuteScript returns a body", exec != null);
        check("script execution returns a result_id",
                exec != null && exec.contains("\"result_id\""));
        check("script execution echoes the target",
                exec != null && exec.contains("com.example.app"));

        // 5. Fetching that result back by id.
        String resultId = null;
        if (exec != null) {
            int at = exec.indexOf("\"result_id\"");
            if (at >= 0) {
                int colon = exec.indexOf(':', at);
                int quote = exec.indexOf('"', colon + 1);
                int end = exec.indexOf('"', quote + 1);
                if (quote > 0 && end > quote) {
                    resultId = exec.substring(quote + 1, end);
                }
            }
        }
        check("the result id could be read", resultId != null);
        if (resultId != null) {
            String result = client.fridaGetResult(resultId);
            check("fridaGetResult returns a body", result != null);
            check("result carries the same id",
                    result != null && result.contains(resultId));
        }

        // 6. A missing result is reported, not silently empty.
        String missing = client.fridaGetResult("r999999");
        check("unknown result id is reported", missing != null
                && missing.contains("no such result"));

        // 7. The hook library is reachable, so the app can pull a script
        //    to run rather than shipping its own copy.
        String hook = client.hookScript("root_detection.js");
        check("hookScript returns a body", hook != null);
        check("hook script is JavaScript",
                hook != null && hook.contains("Java.perform"));

        // 8. A bad token is rejected, so the app can tell the link is
        //    authenticated rather than merely down.
        UnifiedClient bad = new UnifiedClient(host, "wrong-token", 5000);
        check("wrong token is rejected", bad.statusJson() == null
                || bad.statusJson().contains("unauthorized"));

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            passed++;
            System.out.println("  ok   " + name);
        } else {
            fail(name);
        }
    }

    private static void fail(String name) {
        failed++;
        System.out.println("  FAIL " + name);
    }
}
