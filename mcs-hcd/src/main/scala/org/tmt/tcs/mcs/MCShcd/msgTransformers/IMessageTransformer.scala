package org.tmt.tcs.mcs.MCShcd.msgTransformers
import csw.params.commands.ControlCommand
import csw.params.core.states.CurrentState
import csw.params.events.SystemEvent

case class SubystemResponse(commandResponse: Boolean, errorReason: Option[String], errorInfo: Option[String])

trait IMessageTransformer {
  def decodeCommandResponse(responsePacket: Array[Byte]): SubystemResponse

  def encodeMessage(controlCommand: ControlCommand): Array[Byte]
  def decodeEvent(eventName: String, encodedEventData: Array[Byte]): CurrentState
  def encodeEvent(event: SystemEvent): Array[Byte]
  def encodeCurrentState(currentState: CurrentState): Array[Byte]

}
