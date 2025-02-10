import {Component} from '@angular/core';
import {createHighlighter, HighlighterGeneric} from 'shiki'

import {SplitAreaComponent, SplitComponent} from "angular-split";
import {EditorComponent} from "ngx-monaco-editor-v2";
import {FormsModule} from "@angular/forms";
import {HttpClient} from "@angular/common/http";
import {KeyValuePipe, NgForOf} from "@angular/common";
import {DomSanitizer, SafeHtml} from "@angular/platform-browser";

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [SplitComponent, SplitAreaComponent, EditorComponent, FormsModule, NgForOf, KeyValuePipe],
  template: `
    <as-split>
      <as-split-area>

        <div class="full-height">
          <ngx-monaco-editor style="height:100%" [(ngModel)]="grammarCode"
                             [options]="editorOptions" (ngModelChange)="grammarCodeChanged($event)"></ngx-monaco-editor>
        </div>

      </as-split-area>
      <as-split-area>
        <div class="full-height">
          <div>
            <select id="mySelect" [(ngModel)]="selectedSample" (ngModelChange)="onSelectedSampleChange($event)">
              <option *ngFor="let item of samples | keyvalue" [value]="item.value">
                {{ item.key }}
              </option>
            </select>
          </div>
          <div [innerHtml]="formattedCode"></div>
        </div>

      </as-split-area>
      <as-split-area>
        <div #parseResult></div>
      </as-split-area>


    </as-split>
  `,
  styleUrl: './app.component.scss',
  providers: []
})
export class AppComponent {

  samples: { [index: string]: string } = {}

  selectedSample: string = "";
  highlighter: HighlighterGeneric<"1c" | "1c-query" | "abap" | "actionscript-3" | "ada" | "adoc" | "angular-html" | "angular-ts" | "apache" | "apex" | "apl" | "applescript" | "ara" | "asciidoc" | "asm" | "astro" | "awk" | "ballerina" | "bash" | "bat" | "batch" | "be" | "beancount" | "berry" | "bibtex" | "bicep" | "blade" | "bsl" | "c" | "c#" | "c++" | "cadence" | "cairo" | "cdc" | "clarity" | "clj" | "clojure" | "closure-templates" | "cmake" | "cmd" | "cobol" | "codeowners" | "codeql" | "coffee" | "coffeescript" | "common-lisp" | "console" | "coq" | "cpp" | "cql" | "crystal" | "cs" | "csharp" | "css" | "csv" | "cue" | "cypher" | "d" | "dart" | "dax" | "desktop" | "diff" | "docker" | "dockerfile" | "dotenv" | "dream-maker" | "edge" | "elisp" | "elixir" | "elm" | "emacs-lisp" | "erb" | "erl" | "erlang" | "f" | "f#" | "f03" | "f08" | "f18" | "f77" | "f90" | "f95" | "fennel" | "fish" | "fluent" | "for" | "fortran-fixed-form" | "fortran-free-form" | "fs" | "fsharp" | "fsl" | "ftl" | "gdresource" | "gdscript" | "gdshader" | "genie" | "gherkin" | "git-commit" | "git-rebase" | "gjs" | "gleam" | "glimmer-js" | "glimmer-ts" | "glsl" | "gnuplot" | "go" | "gql" | "graphql" | "groovy" | "gts" | "hack" | "haml" | "handlebars" | "haskell" | "haxe" | "hbs" | "hcl" | "hjson" | "hlsl" | "hs" | "html" | "html-derivative" | "http" | "hxml" | "hy" | "imba" | "ini" | "jade" | "java" | "javascript" | "jinja" | "jison" | "jl" | "js" | "json" | "json5" | "jsonc" | "jsonl" | "jsonnet" | "jssm" | "jsx" | "julia" | "kotlin" | "kql" | "kt" | "kts" | "kusto" | "latex" | "lean" | "lean4" | "less" | "liquid" | "lisp" | "lit" | "log" | "logo" | "lua" | "luau" | "make" | "makefile" | "markdown" | "marko" | "matlab" | "md" | "mdc" | "mdx" | "mediawiki" | "mermaid" | "mips" | "mipsasm" | "mmd" | "mojo" | "move" | "nar" | "narrat" | "nextflow" | "nf" | "nginx" | "nim" | "nix" | "nu" | "nushell" | "objc" | "objective-c" | "objective-cpp" | "ocaml" | "pascal" | "perl" | "perl6" | "php" | "plsql" | "po" | "polar" | "postcss" | "pot" | "potx" | "powerquery" | "powershell" | "prisma" | "prolog" | "properties" | "proto" | "protobuf" | "ps" | "ps1" | "pug" | "puppet" | "purescript" | "py" | "python" | "ql" | "qml" | "qmldir" | "qss" | "r" | "racket" | "raku" | "razor" | "rb" | "reg" | "regex" | "regexp" | "rel" | "riscv" | "rs" | "rst" | "ruby" | "rust" | "sas" | "sass" | "scala" | "scheme" | "scss" | "sdbl" | "sh" | "shader" | "shaderlab" | "shell" | "shellscript" | "shellsession" | "smalltalk" | "solidity" | "soy" | "sparql" | "spl" | "splunk" | "sql" | "ssh-config" | "stata" | "styl" | "stylus" | "svelte" | "swift" | "system-verilog" | "systemd" | "talon" | "talonscript" | "tasl" | "tcl" | "templ" | "terraform" | "tex" | "tf" | "tfvars" | "toml" | "ts" | "ts-tags" | "tsp" | "tsv" | "tsx" | "turtle" | "twig" | "typ" | "typescript" | "typespec" | "typst" | "v" | "vala" | "vb" | "verilog" | "vhdl" | "vim" | "viml" | "vimscript" | "vue" | "vue-html" | "vy" | "vyper" | "wasm" | "wenyan" | "wgsl" | "wiki" | "wikitext" | "wl" | "wolfram" | "xml" | "xsl" | "yaml" | "yml" | "zenscript" | "zig" | "zsh" | "文言", "andromeeda" | "aurora-x" | "ayu-dark" | "catppuccin-frappe" | "catppuccin-latte" | "catppuccin-macchiato" | "catppuccin-mocha" | "dark-plus" | "dracula" | "dracula-soft" | "everforest-dark" | "everforest-light" | "github-dark" | "github-dark-default" | "github-dark-dimmed" | "github-dark-high-contrast" | "github-light" | "github-light-default" | "github-light-high-contrast" | "houston" | "kanagawa-dragon" | "kanagawa-lotus" | "kanagawa-wave" | "laserwave" | "light-plus" | "material-theme" | "material-theme-darker" | "material-theme-lighter" | "material-theme-ocean" | "material-theme-palenight" | "min-dark" | "min-light" | "monokai" | "night-owl" | "nord" | "one-dark-pro" | "one-light" | "plastic" | "poimandres" | "red" | "rose-pine" | "rose-pine-dawn" | "rose-pine-moon" | "slack-dark" | "slack-ochin" | "snazzy-light" | "solarized-dark" | "solarized-light" | "synthwave-84" | "tokyo-night" | "vesper" | "vitesse-black" | "vitesse-dark" | "vitesse-light"> | null = null;
  constructor(httpClient: HttpClient, private sanitizer: DomSanitizer) {

    httpClient.get<any>('taxi.tmLanguage.json').subscribe(response => {
      this.grammarCode = JSON.stringify(response, null, 2)
      createHighlighter({
        langs: [response as any],
        themes: ['github-dark-default']
      }).then(r => this.highlighter = r)
    })



    const f = ['sample.taxi', 'sample2.taxi']
      .map(filename => httpClient.get(`samples/${filename}`, {responseType: 'text'}).subscribe(next => {
          this.samples[filename] = next
        })
      )
  }

  languageName = 'Taxi'
  editCount = 0;

  editorOptions = {theme: 'vs-dark', language: 'json'};
  grammarCode = ''

  formattedCode:SafeHtml = ''

  title = 'tmgrammar-playground';

  onSelectedSampleChange($event: any) {
    this.selectedSample = $event;
    this.highlightSelectedSample()
  }

  private highlightSelectedSample() {
    const formatted = this.highlighter!.codeToHtml(this.selectedSample, {
      lang: this.languageName,
      theme: 'github-dark-default'
    })!
    this.formattedCode = this.sanitizer.bypassSecurityTrustHtml(formatted);
  }

  async grammarCodeChanged($event: any) {
    const grammarJson = JSON.parse($event)
    this.editCount++
    this.languageName = `Taxi${this.editCount}`;
    grammarJson.name = this.languageName;
    grammarJson.scopeName = `source.taxi${this.editCount}`;
    await this.highlighter?.loadLanguage(grammarJson)
    this.highlightSelectedSample();
  }
}
