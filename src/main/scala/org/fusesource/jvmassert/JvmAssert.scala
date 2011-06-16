/**
 * Copyright (C) 2011-2011 the original author or authors.
 * See the notice.md file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fusesource.jvmassert

import scala.tools.nsc.Global
import scala.tools.nsc.plugins.Plugin
import scala.tools.nsc.plugins.PluginComponent
import scala.tools.nsc.transform._
import scala.tools.nsc.symtab.Flags._
import collection.mutable.{LinkedHashMap, Stack, HashMap}
import scala.reflect.NameTransformer

/**
 * Scala compiler plugin which integrates Scala assertions with the JVM assertion framework.
 */
class JvmAssert(val global: Global) extends Plugin {

  val name = "jvmassert"
  val description = "Integrates Scala assertions with the JVM assertion framework."
  val components : List[PluginComponent] =  List(JvmAssertInjector)

  object JvmAssertInjector extends PluginComponent with Transform with TypingTransformers  {

    import global._

    val global = JvmAssert.this.global
    val phaseName = "jvmassert"

    val runsAfter = List("parser");
    override val runsBefore =  List[String]("namer")

    class ModuleInfo {
      var using_asserts = false
      var module_exists = false
    }

    protected def newTransformer(unit: CompilationUnit): Transformer = new Transformer {

      override def transform(tree: Tree):Tree = {
        var outer_class:String = null
        var moduleInfoStack = Stack[HashMap[String,ModuleInfo]]()

        def entering[T](clazz:String, ismod:Boolean)(func: =>T):T = {
          if( outer_class==null ) {
            val info = moduleInfoStack.top.getOrElseUpdate(clazz, new ModuleInfo)
            if( ismod ) {
              info.module_exists = true;
            }
            outer_class = clazz
            try {
              func
            } finally {
              outer_class = null
            }
          } else {
            func
          }
        }

        val transformer = new Transformer {
          override def transform(tree: Tree): Tree = {
            tree match {

              case PackageDef(pid, stats) =>

                // Lets the map we push here will
                // track any classes that have assert calls
                // used.
                moduleInfoStack.push(HashMap())
                var new_tree = (super.transform(tree)).asInstanceOf[PackageDef]
                val modules = moduleInfoStack.pop()

                //
                // We may have to create some new modules on the fly here...
                val new_mods:List[Tree] = modules.flatMap { case (module,info) =>
                  if ( info.using_asserts && !info.module_exists ) {
                    // Lets create a SYNTHETIC Module to hold the $enable_assertions val
                    Some(ModuleDef(
                      Modifiers(SYNTHETIC),
                      module,
                      Template(
                        List(TypeTree(definitions.ScalaObjectClass.tpe)),
                        ValDef(Modifiers(0),nme.WILDCARD, TypeTree(NoType),EmptyTree ),
                        List(
                          DefDef(
                            Modifiers(0), nme.CONSTRUCTOR, List(), List(List()),TypeTree(),Block(
                              Apply(Select(Super(This(tpnme.EMPTY), tpnme.EMPTY),nme.CONSTRUCTOR),Nil),
                              Literal(Constant())
                            )
                          )
                        )
                      )
                    ))
                  } else {
                    None
                  }
                }.toList


                // Ok Now lets add the SYNTHETIC $enable_assertions val to the modules..
                val new_stats: List[Tree] = (new_tree.stats ::: new_mods).map { tree =>
                  tree match {

                    case ModuleDef(mods, name, impl) =>
                      val info = modules.get(name.toString())
                      if ( info.isDefined && info.get.using_asserts ) {

                        // Lets add the $enable_assertions val
                        val new_defs = ValDef(
                          Modifiers(SYNTHETIC|FINAL),
                          "$enable_assertions",
                          TypeTree(),
                          Select(Ident("getClass"),"desiredAssertionStatus")
                        ) :: Nil

                        val new_impl = treeCopy.Template(impl, impl.parents, impl.self,  impl.body ::: new_defs)
                        treeCopy.ModuleDef(tree, mods, name, new_impl)

                      } else {
                        tree
                      }

                    case _ =>
                      tree
                  }
                }


                treeCopy.PackageDef(new_tree, pid, new_stats )

              case ClassDef(mods, name, tparams, impl) =>
                entering(name.toString(), false) {
                  super.transform(tree)
                }

              case ModuleDef(mods, name, impl) =>
                entering(name.toString(), true) {
                  super.transform(tree)
                }

              case Apply(method, args) =>

                // Wrap any assert method calls with an if statement.
                method match {
                  case x:Ident if (x.name.toString() == "assert") =>

                    moduleInfoStack.top.get(outer_class).get.using_asserts = true

                    val assertCall = args match {
                      case arg :: Nil =>
                        // If it's  a one arg assert, lets auto
                        // generate the 2nd message arg.

                        val idents_map = LinkedHashMap[Name, Tree]()
                        def add_idents(tree:Tree):Unit = tree match {
                          case Ident(name) => idents_map += name->tree
                          case Apply(method, args) =>
                            method match {
                              case Select(lhs, rhs)=> add_idents(lhs)
                              case x => // println("ignoring : "+x.getClass)
                            }
                            args.foreach(add_idents(_))
                          case x => // println("ignoring: "+x.getClass)
                        }
                        add_idents(arg)
                        val idents = idents_map.values.toList

                        val message = if( idents.isEmpty ) {
                          Literal(Constant(NameTransformer.decode(arg.toString)))
                        } else {
                          val format = arg.toString+" with "+(idents.map(_+"=>%s").mkString(", "))
                          Apply(Select(Literal(Constant(format)),"format"), idents)
                        }

                        treeCopy.Apply(tree, method, List(arg,message))
                      case _ =>
                        tree
                    }

                    If( Select(Ident(outer_class), "$enable_assertions"),
                      assertCall,
                      EmptyTree
                    )

                  case _ =>
                    super.transform(tree)
                }

              case _ =>
                super.transform(tree)
            }
          }
        }

        val result = transformer.transform(tree);
//        println("dump: "+(nodePrinters nodeToString result))
//        treeBrowser browse result
        result
      }

    }
  }

}