package sh.ondr.kmcp.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

class KmcpIrTransformer(
	private val messageCollector: MessageCollector,
	private val pluginContext: IrPluginContext,
) : IrElementTransformerVoid() {
	@OptIn(UnsafeDuringIrConstructionAPI::class)
	override fun visitCall(expression: IrCall): IrExpression {
		return super.visitCall(expression)
	}
}
