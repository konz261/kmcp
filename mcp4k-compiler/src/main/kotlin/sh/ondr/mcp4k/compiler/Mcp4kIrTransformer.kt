package sh.ondr.mcp4k.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irBlock
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionReference
import org.jetbrains.kotlin.ir.expressions.IrSpreadElement
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.constructedClass
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.packageFqName
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

@OptIn(UnsafeDuringIrConstructionAPI::class)
class Mcp4kIrTransformer(
	private val messageCollector: MessageCollector,
	private val pluginContext: IrPluginContext,
	private val isTest: Boolean = false,
) : IrElementTransformerVoid() {
	val initializerFq = if (isTest) {
		"sh.ondr.mcp4k.generated.initializer.Mcp4kTestInitializer"
	} else {
		"sh.ondr.mcp4k.generated.initializer.Mcp4kInitializer"
	}
	private val initializerClassId = ClassId.topLevel(FqName(initializerFq))

	private var currentFile: IrFile? = null

	override fun visitFile(declaration: IrFile): IrFile {
		val previousFile = currentFile
		currentFile = declaration
		declaration.transformChildrenVoid()
		currentFile = previousFile
		return declaration
	}

	override fun visitConstructorCall(expression: IrConstructorCall): IrExpression {
		expression.transformChildrenVoid()

		val callee = expression.symbol.owner
		val isNamedBuilder = callee.constructedClass.name.asString() == "Builder"
		val isInServer = callee.constructedClass.parentClassOrNull?.name?.asString() == "Server"
		val isInRuntimePackage = callee.constructedClass.parentClassOrNull?.packageFqName?.asString() == "sh.ondr.mcp4k.runtime"
		if (isNamedBuilder && isInServer && isInRuntimePackage) {
			val initializerSymbol = pluginContext.referenceClass(initializerClassId) ?: error("Could not find Mcp4kInitializer")

			val builder =
				DeclarationIrBuilder(
					generatorContext = pluginContext,
					symbol = callee.symbol,
					startOffset = expression.startOffset,
					endOffset = expression.endOffset,
				)

			return builder.irBlock(expression = expression) {
				+irGetObject(initializerSymbol)
				+expression
			}
		}

		return expression
	}

	override fun visitFunction(declaration: IrFunction): IrStatement {
		val currentIsWithToolsOrPrompts = declaration.kotlinFqName.asString() in listOf(
			"sh.ondr.mcp4k.runtime.Server.Builder.withTools",
			"sh.ondr.mcp4k.runtime.Server.Builder.withPrompts",
		)
		// Skip withTools/withPrompts declaration, otherwise an error is thrown when
		// calling withTool/withPrompt from inside withTools/withPrompts
		if (currentIsWithToolsOrPrompts) {
			return declaration
		}
		return super.visitFunction(declaration)
	}

	override fun visitCall(expression: IrCall): IrExpression {
		expression.transformChildrenVoid()

		val callee = expression.symbol.owner
		val fqName = callee.fqNameWhenAvailable?.asString() ?: return expression

		val (inServerBuilder, functionName) = fqName.split(".").let {
			val inServerBuilder = it.dropLast(1).joinToString(".") == "sh.ondr.mcp4k.runtime.Server.Builder"
			inServerBuilder to it.last()
		}

		// If not in Server.Builder, or the functionName isn't one we care about, bail out
		if (!inServerBuilder || functionName !in listOf("withTool", "withTools", "withPrompt", "withPrompts")) {
			return expression
		}

		// Otherwise, enforce direct references
		enforceDirectReferences(expression, functionName)
		return expression
	}

	/*
	 * Enforce that arguments to [functionName] are direct references (IrFunctionReference).
	 *
	 * - For withTool/withPrompt, there's a single argument that's a function reference.
	 * - For withTools/withPrompts, there's a single vararg argument containing multiple references.
	 */
	private fun enforceDirectReferences(
		call: IrCall,
		functionName: String,
	) {
		val valueArguments = (0 until call.valueArgumentsCount).mapNotNull { idx -> call.getValueArgument(idx) }

		valueArguments.forEach { arg ->
			when (arg) {
				// For withTools/withPrompts => 1 vararg
				is IrVararg -> {
					arg.elements.forEach { varargElem ->
						when (varargElem) {
							// Disallow spread element
							is IrSpreadElement -> {
								messageCollector.report(
									CompilerMessageSeverity.ERROR,
									"mcp4k: Spread operator not allowed for $functionName if direct references are required.",
									call.getMessageLocation(),
								)
							}
							// Each element in the vararg should be an IrExpression of type IrFunctionReference
							is IrExpression -> checkIsFunctionRef(varargElem, functionName)
							// Should never happen
							else -> error("mcp4k: Unexpected vararg element type: ${varargElem::class.java}")
						}
					}
				}

				// For withTool/withPrompt  => 1 direct argument
				else -> checkIsFunctionRef(arg, functionName)
			}
		}
	}

	private fun checkIsFunctionRef(
		expr: IrExpression,
		functionName: String,
	) {
		if (expr !is IrFunctionReference) {
			messageCollector.report(
				CompilerMessageSeverity.ERROR,
				"mcp4k: Only direct function references (like ::myFunction) are allowed for $functionName",
				expr.getMessageLocation(),
			)
		}
	}

	fun IrExpression.getMessageLocation(): CompilerMessageLocation? {
		if (currentFile == null) return null
		val fileEntry = currentFile!!.fileEntry
		val (line, col) = fileEntry.getLineAndColumnNumbers(startOffset)

		// line, col are 0-based, so +1 to get 1-based display
		val location = CompilerMessageLocation.create(
			fileEntry.name,
			line + 1,
			col + 1,
			null,
		)
		return location
	}
}
