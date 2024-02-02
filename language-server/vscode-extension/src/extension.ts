// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import * as vscode from "vscode";
import { workspace } from "vscode";
import * as path from "path";
import * as findJavaHome from "find-java-home";
import * as fs from "fs";
import { glob } from "glob";

import {
   LanguageClient,
   LanguageClientOptions,
   ServerOptions,
} from "vscode-languageclient";
class CompilerConfig {
   typeChecker: FeatureToggle = FeatureToggle.DISABLED;

   asArgs(): string[] {
      return [`typeChecker=${this.typeChecker}`];
   }
}

type Foo = "A";

enum FeatureToggle {
   ENABLED = "ENABLED",
   DISABLED = "DISABLED",
   SOFT_ENABLED = "SOFT_ENABLED",
}

// this method is called when your extension is activated
// your extension is activated the very first time the command is executed
export function activate(context: vscode.ExtensionContext) {
   // Use the console to output diagnostic information (console.log) and errors (console.error)
   // This line of code will only be executed once when your extension is activated
   console.log("Taxi language extension starting");

   const compilerConfig = new CompilerConfig();

   const taxiConfig = workspace.getConfiguration("taxi");
   const typeCheckerEnabled = (
      taxiConfig.get("typeChecker") as string
   ).toUpperCase() as FeatureToggle;
   compilerConfig.typeChecker = typeCheckerEnabled;

   const definedJavaHome = workspace
      .getConfiguration("taxi")
      .get("javaHome", "");
   if (definedJavaHome !== "") {
      startPlugin(definedJavaHome, context, compilerConfig);
   } else {
      findJavaHome({ allowJre: true }, (err, home) => {
         if (err) {
            return console.log(err);
         }
         startPlugin(home, context, compilerConfig);
      });
   }
}

function startPlugin(
   javaHome: string,
   context: vscode.ExtensionContext,
   config: CompilerConfig
) {
   const enableDebug = workspace
      .getConfiguration("taxi")
      .get("enableDebugSession", false);
   const waitForDebuggerToAttach = workspace
      .getConfiguration("taxi")
      .get("waitForDebuggerToAttach", false);
   if (javaHome) {
      console.log(`Using java from ${javaHome}`);
      // Java execution path.
      let excecutable: string = path.join(javaHome, "bin", "java");

      let classPath: string;
      if (enableDebug) {
         const classPathSeperator = process.platform === "win32" ? ";" : ":";
         // These are the jars that we load direct from the
         // project folders, rather than from the dependencies
         // directory of the language server.
         // This means that when changesare made in these projects and they're recompiled,
         // we pick up the changes from their "target" directory,
         // saving us another maven build cycle.
         const projectJars = [
            "../../../../taxi-lang/core-types",
            "../../../../taxi-lang/compiler",
            "../../../../vyne/vyne-core-types",
            "../../taxi-lang-service",
            "../../taxi-lang-server-standalone",
         ];
         const jarClasspathEntries = projectJars.map((projectJar) => {
            const fullPath = path.join(
               __dirname,
               ...projectJar.split("/"),
               "target",
               "classes"
            );
            // Verify the path exists
            if (!fs.existsSync(fullPath)) {
               console.error(`Error: The path does not exist - ${fullPath}`);
            }
            return fullPath;
         });
         const otherClasspathEntries = [
            "../../taxi-lang-server-standalone/target/dependency/*",
         ].map((projectJar) => path.join(__dirname, ...projectJar.split("/")));

         classPath = jarClasspathEntries
            .concat(otherClasspathEntries)
            .join(classPathSeperator);

         deleteJarFileFromDependencies(projectJars);
      } else {
         const jarName = "taxi-lang-server-jar-with-dependencies.jar";
         classPath = path.join(__dirname, jarName);
      }

      // let classPath = (useDebugJar) ? path.join(__dirname, '..', '..', 'taxi-lang-server-standalone', 'target', jarName) : path.join(__dirname, jarName);
      const debugSettings = enableDebug
         ? [
              `-agentlib:jdwp=transport=dt_socket,server=y,suspend=${
                 waitForDebuggerToAttach ? "y" : "n"
              },address=5005,quiet=y`,
           ]
         : [];
      const args: string[] = debugSettings.concat(["-cp", classPath]);
      if (enableDebug) {
         console.log(JSON.stringify(args));
      }
      if (waitForDebuggerToAttach) {
         console.warn("Taxi extension will wait for the debugger to attach");
      }

      // Set the server options
      // -- java execution path
      // -- argument to be pass when executing the java command
      // Name of the launcher class which contains the main.
      const main: string = "lang.taxi.lsp.Launcher";

      let serverOptions: ServerOptions = {
         command: excecutable,
         args: [...args, main].concat(config.asArgs()),
         options: {},
      };

      if (enableDebug) {
         console.log("Server options:", serverOptions);
      }

      // Options to control the language client
      let clientOptions: LanguageClientOptions = {
         // Register the server for plain text documents
         documentSelector: [
            { scheme: "file", language: "taxi" },
            { scheme: "file", pattern: "**/taxi.conf" },
         ],
         synchronize: {
            // Notify the server about file changes to .taxi files contained in the workspace
            fileEvents: workspace.createFileSystemWatcher(
               "{**/*.taxi,**/taxi.conf}"
            ),
         },
         // middleware: {
         //    workspace: {
         //       // Respond to server-side requests for configuration.
         //       // this lets the server ask for how the user has configured things, such as logging
         //       configuration: (params) => {
         //          const configSettings = params.items.map((item) => {
         //             if (item.section) {
         //                // Return the specific configuration for "taxi" as requested by the server
         //                const config = vscode.workspace.getConfiguration(
         //                   item.section,
         //                   null
         //                );
         //                return config.get(item.section);
         //             }
         //          });
         //          return Promise.resolve(configSettings);
         //       },
         //    },
         // },
      };

      // Create the language client and start the client.
      let disposable = new LanguageClient(
         "Taxi",
         "Taxi Language Server",
         serverOptions,
         clientOptions
      ).start();

      // Disposables to remove on deactivation.
      context.subscriptions.push(disposable);
   }
}

// this method is called when your extension is deactivated
export function deactivate() {
   console.log("taxi-language-server is deactivated");
}

/**
 * Removes a jar file from the dependencies folder of taxi-lang-server-standalone,
 * forcing the classpath to pick it up from the target folder of it's own project.
 * @param projectJars
 */
function deleteJarFileFromDependencies(dependencies: string[]) {
   dependencies.forEach((dependency) => {
      // Extract the last part of the path as filePattern
      const filePattern = dependency.split("/").pop();
      // Construct the pattern for matching files to delete
      const pattern = path.join(
         __dirname,
         `../../taxi-lang-server-standalone/target/dependency/${filePattern}*.jar`
      );

      // Use glob to find matching files
      glob(pattern, (err, files) => {
         if (err) {
            console.error(`Error finding files with pattern ${pattern}:`, err);
            return;
         }

         // Delete each matching file
         files.forEach((file) => {
            fs.unlink(file, (err) => {
               if (err) {
                  console.error(`Error deleting file ${file}:`, err);
               } else {
                  console.log(`Deleted dependency jar ${file} to force loading local compiled version`);
               }
            });
         });
      });
   });
}
