import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtCallExpression
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import java.io.File
fun main(args:Array<String>){
 val disposable=Disposer.newDisposable();var errors=0;var files=0
 try {
  val environment=KotlinCoreEnvironment.createForProduction(disposable,CompilerConfiguration(),EnvironmentConfigFiles.JVM_CONFIG_FILES)
  val factory=KtPsiFactory(environment.project)
  File(args[0]).walkTopDown().filter{it.extension=="kt"||it.extension=="kts"}.forEach{f->
   files++;val parsed=factory.createFile(f.name,f.readText())
   PsiTreeUtil.findChildrenOfType(parsed,KtCallExpression::class.java).forEach{call->
    val named=call.valueArguments.mapNotNull{it.getArgumentName()?.asName?.asString()}
    if(named.size!=named.toSet().size){errors++;println("ERROR ${f.path}:${call.textOffset}: duplicate named argument ${call.calleeExpression?.text}")}
   }
   PsiTreeUtil.findChildrenOfType(parsed,PsiErrorElement::class.java).forEach{e->errors++;println("ERROR ${f.path}:${e.textOffset}: ${e.errorDescription}")}
  }
  println("Parsed $files Kotlin files; $errors syntax errors. This is NOT Android type-checking or a Gradle build.")
 }finally{Disposer.dispose(disposable)}
 check(errors==0)
}
