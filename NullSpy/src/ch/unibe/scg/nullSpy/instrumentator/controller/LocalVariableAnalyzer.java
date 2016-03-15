package ch.unibe.scg.nullSpy.instrumentator.controller;

import java.util.ArrayList;
import java.util.HashMap;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ExceptionTable;
import javassist.bytecode.InstructionPrinter;
import javassist.bytecode.LineNumberAttribute;
import javassist.bytecode.LocalVariableAttribute;
import javassist.bytecode.Mnemonic;
import javassist.bytecode.Opcode;

/**
 * Instruments test-code after locVars.
 * 
 * @author Lina Tran
 *
 */
public class LocalVariableAnalyzer extends VariableAnalyzer implements Opcode {

	public LocalVariableAnalyzer(CtClass cc) {
		super(cc);
	}

	/**
	 * Checks all locVars in a class and instrument test-code after their
	 * assignments.
	 * 
	 * @throws BadBytecode
	 * @throws CannotCompileException
	 * @throws NotFoundException
	 */
	public void instrumentAfterLocVarAssignment() throws BadBytecode,
			CannotCompileException, NotFoundException {

		for (CtMethod method : cc.getDeclaredMethods()) {

			// get everything what is needed for checking locVars in the
			// byte code
			CodeAttribute codeAttribute = method.getMethodInfo()
					.getCodeAttribute();
			CodeIterator codeIterator = codeAttribute.iterator();

			LocalVariableAttribute locVarTable = (LocalVariableAttribute) codeAttribute
					.getAttribute(LocalVariableAttribute.tag);

			HashMap<Integer, Integer> lineNumberMap = getLineNumberTable(method);
			LineNumberAttribute lineNrTable = (LineNumberAttribute) codeAttribute
					.getAttribute(LineNumberAttribute.tag);
			ExceptionTable exceptionTables = codeAttribute.getExceptionTable();

			codeIterator.begin();

			instrumentAfterLocVarObject(method, codeIterator, locVarTable,
					lineNumberMap, lineNrTable, exceptionTables);

			// calculates the time modified project uses
			addTimeToModifiedProject(method);

		}
	}

	private void addTimeToModifiedProject(CtMethod method)
			throws CannotCompileException {
		if (method.getName().equals("main")) {
			CtField f = CtField.make("public static long startTime;", cc);
			cc.addField(f);
			method.insertBefore("startTime = System.nanoTime();");
			method.insertAfter("System.out.println(\"\\nOriginal class time: \" +((System.nanoTime() - startTime)/1000000) + \" ms\");");

			// CtClass etype = ClassPool.getDefault().get(
			// "java.io.IOException");
			// method.addCatch("{ System.out.println($e); throw $e; }",
			// etype);
		}
	}

	/**
	 * Searches only locVar which are objects and directly instrument test-code.
	 * 
	 * @param method
	 * @param codeIterator
	 * @param locVarTable
	 * @param lineNumberMap
	 * @param exceptionTable
	 * @throws BadBytecode
	 * @throws CannotCompileException
	 */
	private void instrumentAfterLocVarObject(CtMethod method,
			CodeIterator codeIterator, LocalVariableAttribute locVarTable,
			HashMap<Integer, Integer> lineNumberMap,
			LineNumberAttribute lineNumberTable, ExceptionTable exceptionTable)
			throws BadBytecode, CannotCompileException {

		// store current instruction and the previous instructions
		ArrayList<Integer> instrPositions = new ArrayList<Integer>();

		int instrCounter = 0;
		int prevInstrOp = 0;

		int methodMaxPc = lineNumberTable
				.startPc(lineNumberTable.tableLength() - 1);

		ArrayList<Integer> exceptionTableEndPosList = getExceptionTableEndPosList(exceptionTable);

		int afterCatchBlockGotoPos = 0;
		int exceptionTableEntryIndex = 0;

		// int endTryBlockPos =
		// getExceptionTableEndPos(exceptionTableEntryIndex,
		// exceptionTable);

		while (codeIterator.hasNext()) {
			int pos = codeIterator.next();
			instrPositions.add(pos);

			int op = codeIterator.byteAt(pos);

			if (instrCounter > 0)
				prevInstrOp = codeIterator.byteAt(instrPositions
						.get(instrCounter - 1));
			instrCounter++;

			// System.out.println(InstructionPrinter.instructionString(
			// codeIterator, pos, method.getMethodInfo2().getConstPool()));

			if (Mnemonic.OPCODE[op].matches("goto .*")) {
				System.out.println(cc.getName());
				System.out.println(method.getName());
				System.out.println(Mnemonic.OPCODE[op]);

				exceptionTableEndPosList.remove(0);
				afterCatchBlockGotoPos = getAfterCatchBlockGotoDestPos(method,
						pos);
				String s = InstructionPrinter.instructionString(codeIterator,
						pos, method.getMethodInfo2().getConstPool());
				System.out.println(s);

				boolean inCatchBlock = true;
				while (inCatchBlock) {
					pos = codeIterator.next();
					op = codeIterator.byteAt(pos);

					if (pos == afterCatchBlockGotoPos) {
						inCatchBlock = false;
						// exceptionTableEntryIndex++;
						// endTryBlockPos = getExceptionTableEndPos(
						// exceptionTableEntryIndex, exceptionTable);
					}
				}
			}

			if (exceptionTable.size() > 0
					&& pos == exceptionTableEndPosList.get(0)
					&& !Mnemonic.OPCODE[op].matches("goto.*")) {
				return;
			}

			if (isLocVarObject(op)
					&& (!Mnemonic.OPCODE[prevInstrOp].matches("goto.*") && pos <= methodMaxPc)) {

				int locVarIndexInLocVarTable = getLocVarIndexInLocVarTable(
						codeIterator, locVarTable, pos, "astore.*");
				String localVariableName = locVarTable
						.variableName(locVarIndexInLocVarTable);
				int localVariableLineNumber = getLineNumber(lineNumberMap, pos);

				adaptByteCode(method, localVariableName,
						localVariableLineNumber, "localVariable",
						"localVariable");
			}
		}
	}

	private ArrayList<Integer> getExceptionTableEndPosList(
			ExceptionTable exceptionTable) {
		ArrayList<Integer> exceptionTableEndPosList = new ArrayList<>();
		int handlerPos = 0;
		for (int i = 0; i < exceptionTable.size(); i++) {
			if (exceptionTableEndPosList.size() == 0) {
				exceptionTableEndPosList.add(exceptionTable.endPc(i));
				handlerPos = exceptionTable.handlerPc(i);
			} else {
				if (exceptionTable.handlerPc(i) == handlerPos) {
					exceptionTableEndPosList.remove(exceptionTableEndPosList
							.size() - 1);
					exceptionTableEndPosList.add(exceptionTable.endPc(i));
					handlerPos = exceptionTable.handlerPc(i);
				} else {
					exceptionTableEndPosList.add(exceptionTable.endPc(i));
					handlerPos = exceptionTable.handlerPc(i);
				}
			}
		}

		return exceptionTableEndPosList;
	}

	// private int getExceptionTableEndPos(int exceptionTableEntryIndex,
	// ExceptionTable exceptionTable) {
	//
	// int endTryBlockPos = 0;
	// int handlerPos = 0;
	//
	// for (int i = exceptionTableEntryIndex; i < exceptionTable.size(); i++) {
	// if (endTryBlockPos == 0) {
	// endTryBlockPos = exceptionTable.endPc(i);
	// handlerPos = exceptionTable.handlerPc(i);
	// } else {
	// if (exceptionTable.handlerPc(i) > handlerPos) {
	// break;
	// } else {
	// endTryBlockPos = exceptionTable.endPc(i);
	// }
	//
	// }
	// }
	//
	// return endTryBlockPos;
	// }

	private int getAfterCatchBlockGotoDestPos(CtMethod method, int pos) {
		CodeIterator codeIterator = method.getMethodInfo().getCodeAttribute()
				.iterator();
		String goto_X = InstructionPrinter.instructionString(codeIterator, pos,
				method.getMethodInfo2().getConstPool());
		System.out.println(goto_X);
		return Integer.parseInt(goto_X.substring(goto_X.indexOf(" ") + 1,
				goto_X.length()));
	}

	/**
	 * Checks if the locVar is an object, NOT a primitive one.
	 * 
	 * @param op
	 * @return
	 */
	private static boolean isLocVarObject(int op) {
		return Mnemonic.OPCODE[op].matches("a{1,2}store.*");
	}

}