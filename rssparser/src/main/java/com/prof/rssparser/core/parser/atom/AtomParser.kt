/*
*   Copyright 2016 Marco Gomiero
*
*   Licensed under the Apache License, Version 2.0 (the "License");
*   you may not use this file except in compliance with the License.
*   You may obtain a copy of the License at
*
*       http://www.apache.org/licenses/LICENSE-2.0
*
*   Unless required by applicable law or agreed to in writing, software
*   distributed under the License is distributed on an "AS IS" BASIS,
*   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*   See the License for the specific language governing permissions and
*   limitations under the License.
*
*/

package com.prof.rssparser.core.parser.atom

import com.prof.rssparser.Article
import com.prof.rssparser.Channel
import com.prof.rssparser.Image
import com.prof.rssparser.ItunesArticleData
import com.prof.rssparser.ItunesChannelData
import com.prof.rssparser.core.CoreXMLParser.getImageUrl
import com.prof.rssparser.utils.AtomKeyword
import com.prof.rssparser.utils.attributeValue
import com.prof.rssparser.utils.contains
import com.prof.rssparser.utils.nextTrimmedText
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException


internal fun extractAtomContent(xmlPullParser: XmlPullParser): Channel {
    val channelBuilder = Channel.Builder()
    var articleBuilder = Article.Builder()
    val channelImageBuilder = Image.Builder()
    val itunesChannelBuilder = ItunesChannelData.Builder()
    var itunesArticleBuilder = ItunesArticleData.Builder()

    // This image url is extracted from the content and the description of the rss item.
    // It's a fallback just in case there aren't any images in the enclosure tag.
    var imageUrlFromContent: String? = null

    // A flag just to be sure of the correct parsing
    var insideItem = false
    var insideChannel = false

    var eventType = xmlPullParser.eventType

    // Start parsing the xml
    loop@ while (eventType != XmlPullParser.END_DOCUMENT) {

        // Start parsing the item
        when {
            eventType == XmlPullParser.START_TAG -> when {
                // Entering conditions
                xmlPullParser.contains(AtomKeyword.Atom) -> {
                    insideChannel = true
                }

                xmlPullParser.contains(AtomKeyword.Entry.Item) -> {
                    insideItem = true
                }
                //endregion

                //region Channel tags
                xmlPullParser.contains(AtomKeyword.Icon) -> {
                    channelImageBuilder.url(xmlPullParser.nextTrimmedText())
                }
                //endregion

                //region Item tags
                xmlPullParser.contains(AtomKeyword.Entry.Author) -> {
                    if (insideItem) {
                        articleBuilder.author(xmlPullParser.nextTrimmedText())
                    }
                }

                xmlPullParser.contains(AtomKeyword.Entry.Category) -> {
                    if (insideItem) {
                        val nextText = xmlPullParser.nextTrimmedText()
                        val termAttributeValue = xmlPullParser.attributeValue(AtomKeyword.Entry.Term)

                        /**
                         * We want to look at the 'term' attribute and use that if no text is present
                         * such as `<category term="android"/>`
                         */
                        val categoryText = if (nextText?.isEmpty() == true) {
                            termAttributeValue
                        } else {
                            nextText
                        }
                        articleBuilder.addCategory(categoryText)
                    }
                }

                xmlPullParser.contains(AtomKeyword.Entry.Guid) -> {
                    if (insideItem) {
                        articleBuilder.guid(xmlPullParser.nextTrimmedText())
                    }
                }

                xmlPullParser.contains(AtomKeyword.Entry.Content) -> {
                    if (insideItem) {
                        val content = try {
                            xmlPullParser.nextTrimmedText()
                        } catch (e: XmlPullParserException) {
                            // If there's some html not escaped, the parsing is going to fail
                            null
                        }
                        articleBuilder.content(content)
                        imageUrlFromContent = getImageUrl(content)
                    }
                }

                xmlPullParser.contains(AtomKeyword.Updated) -> {
                    when {
                        insideItem -> {
                            articleBuilder.pubDateIfNull(xmlPullParser.nextTrimmedText())
                        }
                        insideChannel -> {
                            channelBuilder.lastBuildDate(xmlPullParser.nextTrimmedText())
                        }
                    }
                }

                xmlPullParser.contains(AtomKeyword.Entry.Published) -> {
                    if (insideItem) {
                        articleBuilder.pubDateIfNull(xmlPullParser.nextTrimmedText())
                    }
                }

                xmlPullParser.contains(AtomKeyword.Subtitle) -> {
                    if (insideChannel) {
                        channelBuilder.description(xmlPullParser.nextTrimmedText())
                    }
                }

                xmlPullParser.contains(AtomKeyword.Entry.Description) -> {
                    if (insideItem) {
                        val description = xmlPullParser.nextTrimmedText()
                        articleBuilder.description(description)
                        imageUrlFromContent = getImageUrl(description)
                    }
                }
                //region Mixed tags
                xmlPullParser.contains(AtomKeyword.Title) -> {
                    if (insideChannel) {
                        when {
                            insideItem -> articleBuilder.title(xmlPullParser.nextTrimmedText())
                            else -> channelBuilder.title(xmlPullParser.nextTrimmedText())
                        }
                    }
                }

                xmlPullParser.contains(AtomKeyword.Link) -> {
                    if (insideChannel) {
                        val href = xmlPullParser.attributeValue(
                            AtomKeyword.Link.Href
                        )
                        val rel = xmlPullParser.attributeValue(
                            AtomKeyword.Link.Rel
                        )
                        if (rel != AtomKeyword.Link.Edit.value) {
                            when {
                                insideItem -> articleBuilder.link(href)
                                else -> channelBuilder.link(href)
                            }
                        }
                    }
                }
            }

            // Exit conditions
            eventType == XmlPullParser.END_TAG && xmlPullParser.contains(AtomKeyword.Entry.Item) -> {
                // The item is correctly parsed
                insideItem = false
                // Set data
                articleBuilder.imageIfNull(imageUrlFromContent)
                articleBuilder.itunesArticleData(itunesArticleBuilder.build())
                channelBuilder.addArticle(articleBuilder.build())
                // Reset temp data
                imageUrlFromContent = null
                articleBuilder = Article.Builder()
                itunesArticleBuilder = ItunesArticleData.Builder()
            }

            eventType == XmlPullParser.END_TAG && xmlPullParser.contains(AtomKeyword.Atom) -> {
                // The channel is correctly parsed
                insideChannel = false
            }
        }
        eventType = xmlPullParser.next()
    }

    val channelImage = channelImageBuilder.build()
    if (channelImage.isNotEmpty()) {
        channelBuilder.image(channelImage)
    }
    channelBuilder.itunesChannelData(itunesChannelBuilder.build())

    return channelBuilder.build()
}
