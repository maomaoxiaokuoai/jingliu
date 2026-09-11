import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.*
import com.intellij.openapi.util.Disposer
import com.intellij.psi.util.PsiTreeUtil
import java.io.File

/** Name/arity contract only. Does not resolve Compose/Android parameter types or execute UI. */
fun main(args:Array<String>){
 val disposable=Disposer.newDisposable()
 try {
  val env=KotlinCoreEnvironment.createForProduction(disposable,CompilerConfiguration(),EnvironmentConfigFiles.JVM_CONFIG_FILES)
  val factory=KtPsiFactory(env.project)
  val input=File(args[0],"app/src/main/java").walkTopDown().filter{it.extension=="kt"}.map{it to factory.createFile(it.name,it.readText())}.toList()
  val names=setOf("GlassSurface","GlassButtonBody","GlassTextButton","GlassOutlinedButton","GlassIconButton","GlassFilterChip","GlassRadioButton","GlassSelectionMark","GlassLinearProgressIndicator","GlassCircularProgressIndicator","GlassTopAppBar","GlassSnackbar","GlassWindow","GlassBadge","LiquidActionButton","LiquidSwitch","LiquidSlider")
  val signatures=mutableMapOf<String,MutableList<List<String>>>()
  input.forEach{(_,tree)->PsiTreeUtil.findChildrenOfType(tree,KtNamedFunction::class.java).forEach{fn->if(fn.name in names)signatures.getOrPut(fn.name!!){mutableListOf()}.add(fn.valueParameters.mapNotNull{it.name})}}
  var calls=0;var failures=0
  input.forEach{(file,tree)->PsiTreeUtil.findChildrenOfType(tree,KtCallExpression::class.java).forEach{call->
   val name=call.calleeExpression?.text
   if(name in names){calls++
    val actualArguments=call.valueArguments
    val valid=signatures[name].orEmpty().any{params->
     val taken=mutableSetOf<String>();var index=0
     actualArguments.all{arg->val key=if(arg is KtLambdaArgument)params.lastOrNull() else arg.getArgumentName()?.asName?.asString() ?: params.getOrNull(index)
      if(arg !is KtLambdaArgument)index++;key!=null&&key in params&&taken.add(key)}
    }
    if(!valid){failures++;println("FAIL ${file.path}:${call.textOffset} $name argument names/positions")}
   }
  }}
  println("Glass call audit: $calls call sites, $failures name/position mismatches. NOT Android type-checking.")
  check(failures==0)
 }finally{Disposer.dispose(disposable)}
}
