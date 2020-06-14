package com.warburg.somelang.common

/**
 * Types used to mark objects as having been visited by a particular phase of the compiler.
 *
 * A given phase inherits from phases it depends on, since completing that phase implies that its dependencies have been completed as well.
 *
 * @author ewarburg
 */
interface Phase

interface PrimordialPhase : Phase
interface LexingPhase : PrimordialPhase
interface ParsingPhase : LexingPhase
interface NameResolvingPhase : ParsingPhase
interface TypecheckingPhase : NameResolvingPhase
