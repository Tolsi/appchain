package ru.tolsi.appchain

import akka.util.Timeout

case class ContractExecutionLimits(inputParamsMaxLength: Int, resultMaxLength: Int, timeout: Timeout)