package sh.ondr.kmcp.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrAnonymousInitializer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.packageFqName
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

@OptIn(UnsafeDuringIrConstructionAPI::class)
class KmcpIrTransformer(
	private val messageCollector: MessageCollector,
	private val pluginContext: IrPluginContext,
) : IrElementTransformerVoid() {
	val pkg = "sh.ondr.kmcp"
	private val registryClassId = ClassId.topLevel(FqName("$pkg.generated.KmcpGeneratedToolRegistryInitializer"))

	override fun visitClass(declaration: IrClass): IrStatement {
		super.visitClass(declaration)
		if (declaration.name.asString() == "ToolMeta" &&
			declaration.packageFqName?.asString() == "sh.ondr.kmcp.runtime.tools"
		) {
			val companion = declaration.declarations.filterIsInstance<IrClass>().find { it.isCompanion }
			companion?.declarations?.filterIsInstance<IrAnonymousInitializer>()?.forEach { initBlock ->
				val initBody = initBlock.body
				val call = createKmcpInitCall(initBlock.startOffset, initBlock.endOffset)
				initBody.statements.add(0, call)
			}
		}
		return declaration
	}

	private fun createKmcpInitCall(
		startOffset: Int,
		endOffset: Int,
	): IrStatement {
		val registryClassSymbol =
			pluginContext.referenceClass(registryClassId)
				?: error("Could not find KmcpGeneratedToolRegistryInitializer class")

		// IR expression that gets instance of the generated object.
		return IrGetObjectValueImpl(
			startOffset = startOffset,
			endOffset = endOffset,
			type = registryClassSymbol.owner.defaultType,
			symbol = registryClassSymbol,
		)
	}
}
