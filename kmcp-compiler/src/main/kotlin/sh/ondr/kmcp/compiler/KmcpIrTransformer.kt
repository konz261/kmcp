package sh.ondr.kmcp.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irBlock
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.constructedClass
import org.jetbrains.kotlin.ir.util.packageFqName
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

@OptIn(UnsafeDuringIrConstructionAPI::class)
class KmcpIrTransformer(
	private val messageCollector: MessageCollector,
	private val pluginContext: IrPluginContext,
) : IrElementTransformerVoid() {
	private val pkg = "sh.ondr.kmcp"
	private val registryClassId = ClassId.topLevel(FqName("$pkg.generated.KmcpGeneratedInitializer"))

	override fun visitConstructorCall(expression: IrConstructorCall): IrExpression {
		expression.transformChildrenVoid()

		val callee = expression.symbol.owner
		val isNamedBuilder = callee.constructedClass.name.asString() == "Builder"
		val isInServer = callee.constructedClass.parentClassOrNull?.name?.asString() == "Server"
		val isInRuntimePackage = callee.constructedClass.parentClassOrNull?.packageFqName?.asString() == "sh.ondr.kmcp.runtime"
		if (isNamedBuilder && isInServer && isInRuntimePackage) {
			val initializerSymbol = pluginContext.referenceClass(registryClassId) ?: error("Could not find KmcpGeneratedInitializer class")

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
}
