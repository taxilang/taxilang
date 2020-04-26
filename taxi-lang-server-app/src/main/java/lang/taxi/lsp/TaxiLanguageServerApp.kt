package lang.taxi.lsp

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class TaxiLanguageServerApp {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val app = SpringApplication(TaxiLanguageServerApp::class.java)
            app.run(*args)
        }
    }
}