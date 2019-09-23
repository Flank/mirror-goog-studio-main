package com.android.aaptcompiler

import java.io.InputStream
import javax.xml.stream.XMLEventReader
import javax.xml.stream.XMLStreamException
import javax.xml.stream.events.Attribute
import javax.xml.stream.events.StartElement
import javax.xml.stream.events.XMLEvent

/**
 * Processes a XML file as a android resource.
 *
 * This consists of three primary processes:
 * 1. Collect created ID resources.
 * Gather all ids specified in xml attributes with the create marker. i.e.
 *
 *     <TextView
 *       android:id="@+id/player_name"
 *       android:text="@+string/player_name"/>
 * In this XML, the resource id/player_name would be exported by this xml file, since it has the "+"
 * resource creation marker. Even though string/player_name is marked with the creation marker, it
 * is not an id resource and is ignored.
 *
 * 2. Outline inlined XML resources.
 * In android XML files (drawables, layouts, etc.) it is possible to write an XML resource inside of
 * another XML resource.
 *
 * Consider drawable/player_background:
 *     <shape
 *       xmlns:android="http://schemas.android.com/apk/res/android"
 *       android:shape="rectangle">
 *       <gradient
 *         android:startColor="#FF000000"
 *         android:endColor="#FFAA0000"
 *         android:angle="35" />
 *       <corners
 *         android:radius="10dp" />
 *     </shape>
 * And consider:
 *     <TextView
 *       android:id="@+id/player_name"
 *       android:text="@string/player_name"
 *       android:background=@"drawable/player_background"/>
 *
 * If this drawable is only used here we can simplify this to one file:
 *     <TextView
 *       android:id="@+id/player_name"
 *       android:text="@string/player_name">
 *       <aapt:attr name="android:background">
 *         <shape
 *           xmlns:android="http://schemas.android.com/apk/res/android"
 *           android:shape="rectangle">
 *           <gradient
 *             android:startColor="#FF000000"
 *             android:endColor="#FFAA0000"
 *             android:angle="35" />
 *           <corners
 *             android:radius="10dp" />
 *         </shape>
 *       </aapt:attr>
 *     </TextView>
 *
 * This is possible by outlining the aapt_attr element into a separate XML file. Effectively undoing
 * the inlining by the developer. So this class outlines the inline XML and sets the parent elements
 * attribute to reference the oultined attr.
 *
 * 3. Flatten XMLs to proto.
 * Flatten all XML files (including outlined XMLs in (2.)) to proto to be written as output.
 *
 * The XMLProcessor handles all of these processes simultaneously to require only a single pass over
 * the XML file.
 *
 * @property source: The source that this processor is going to process.
 */
class XmlProcessor(val source: Source) {

  /**
   * All ids found in the XML resource.
   */
  var createdIds= listOf<SourcedResourceName>()
    private set

  /**
   * Processes the XML resource.
   *
   * Outlines all inline XML aapt:attr resources, collects all created id resources, and flattens
   * the resulting XMLs to proto.
   */
  fun process(inputFile: InputStream): Boolean {
    var eventReader: XMLEventReader? = null
    try {
      eventReader = xmlInputFactory.createXMLEventReader(inputFile)
      val collectedIds = mutableMapOf<ResourceName, SourcedResourceName>()

      val documentStart = eventReader.nextEvent()
      if (!documentStart.isStartDocument) {
        // TODO(b/139297538): diagnostics
        return false
      }

      var rootStart: XMLEvent? = null
      while(eventReader.hasNext()) {
        rootStart = eventReader.nextEvent()
        // ignore comments and text before the root tag
        if (rootStart.isStartElement) {
          break
        }
      }
      rootStart ?: return true

      val noError = processElement(rootStart.asStartElement(), eventReader, collectedIds)

      createdIds = collectedIds.values.toList().sortedBy { it.name }

      return noError

    } catch (xmlException: XMLStreamException) {
      val message = xmlException.message ?: ""
      if (!message.contains("Premature end of file", true)) {
        // Having no root is not an error, but any other xml format exception is.
        throw xmlException
      }
      return true
    } finally {
      eventReader?.close()
    }
  }

  private fun processElement(
    startElement: StartElement,
    eventReader: XMLEventReader,
    collectedIds: MutableMap<ResourceName, SourcedResourceName>): Boolean {

    // First, gather any new ids in the attributes of the element.
    var noError = gatherIds(startElement, collectedIds)

    while (eventReader.hasNext()) {
      val nextEvent = eventReader.nextEvent()

      if (nextEvent.isEndElement) {
        // We're done with the current element.
        break
      }

      if (nextEvent.isStartElement) {
        // We're going down a level, so process that element.
        if (!processElement(nextEvent.asStartElement(), eventReader, collectedIds)) {
          noError = false
        }
        continue
      }
    }

    return noError
  }

  private fun gatherIds(
    startElement: StartElement,
    collectedIds: MutableMap<ResourceName, SourcedResourceName>
  ): Boolean {
    var noError = true

    val iterator = startElement.attributes
    while (iterator.hasNext()) {
      val attribute = iterator.next() as Attribute

      val parsedRef = parseReference(attribute.value)
      parsedRef ?: continue

      val resourceName = parsedRef.reference.name
      if (parsedRef.createNew && resourceName.type == AaptResourceType.ID) {
        if (!isValidResourceEntryName(resourceName.entry!!)) {
          // TODO(b/139297538): diagnostics
          noError = false
        } else {
          collectedIds.putIfAbsent(
            resourceName,
            SourcedResourceName(resourceName, startElement.location.lineNumber))
        }
      }
    }
    return noError
  }
}