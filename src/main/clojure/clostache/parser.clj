(ns clostache.parser
  "A parser for mustache templates."
  (:use [clojure.contrib.string :only (map-str)])
  (:import java.util.regex.Matcher))

(defrecord Section [name body start end inverted])

(defn- replace-all
  "Applies all replacements from the replacement list to the string."
  [string replacements]
  (reduce (fn [string [from to dont-quote]]
            (.replaceAll string from
                         (if dont-quote to (Matcher/quoteReplacement to))))
          string replacements))

(defn- escape-html
  "Replaces angle brackets with the respective HTML entities."
  [string]
  (replace-all string [["&" "&amp;"]
                       ["\"" "&quot;"]
                       ["<" "&lt;"]
                       [">" "&gt;"]]))

(defn- create-variable-replacements
  "Creates pairs of variable replacements from the data."
  [data]
  (apply concat
         (for [k (keys data)]
           (let [var-name (name k)
                 var-value ((fn [x] (if (nil? x) "" x)) (k data))]
             (if (instance? String var-value)
               [[(str "\\{\\{\\{\\s*" var-name "\\s*\\}\\}\\}") var-value]
                [(str "\\{\\{\\&s*" var-name "\\s*\\}\\}") var-value]
                [(str "\\{\\{\\s*" var-name "\\s*\\}\\}")
                 (escape-html var-value)]])))))

(defn- remove-comments
  "Removes comments from the template."
  [template]
  (let [comment-regex "\\{\\{\\![^\\}]*\\}\\}"]
    (replace-all template [[(str "(^|[\n\r])[ \t]*" comment-regex
                                 "(\r\n|[\r\n]|$)") "$1" true]
                           [comment-regex ""]])))

(defn- extract-section
  "Extracts the outer section from the template."
  [template]
  (let [start (.indexOf template "{{#")
        start-inverted (.indexOf template "{{^")
        end-tag (.indexOf template "{{/" start)
        end (+ (.indexOf template "}}" end-tag) 2)]
    (if (or (and (= start -1) (= start-inverted -1))
            (= end 1))
      nil
      (let [inverted (= start -1)
            start (if inverted start-inverted start)
            section (.substring template start end)
            body-start (+ (.indexOf section "}}") 2)
            body-end (.lastIndexOf section "{{")
            body (.substring section body-start body-end)
            section-name (.trim (.substring section 3 (- body-start 2)))]
        (Section. section-name body start end inverted)))))

(defn- remove-all-tags
  "Removes all tags from the template."
  [template]
  (replace-all template [["\\{\\{\\S*\\}\\}" ""]]))

(defn- escape-regex
  "Escapes characters that have special meaning in regular expressions."
  [regex]
  (let [chars-to-escape ["\\" "{" "}" "[" "]" "(" ")" "." "?" "^" "+" "-" "|"]]
    (replace-all regex (map #(repeat 2 (str "\\" %)) chars-to-escape))))

(defn- process-set-delimiters
  "Replaces custom set delimiters with mustaches."
  [template]
  (let [builder (StringBuilder. template)
        open-delim (atom "\\{\\{")
        close-delim (atom "\\}\\}")
        set-delims (fn [open close]
                     (doseq [[var delim]
                             [[open-delim open] [close-delim close]]]
                       (swap! var (constantly (escape-regex delim)))))]
    (loop [offset 0]
      (let [string (.toString builder)
            matcher (re-matcher
                     (re-pattern (str @open-delim ".*?" @close-delim))
                     string)]
        (if (.find matcher offset)
          (let [match-result (.toMatchResult matcher)
                match-start (.start match-result)
                match-end (.end match-result)
                match (.substring string match-start match-end)]
            (if-let [delim-change (re-find
                                   (re-pattern (str @open-delim "=(.*?) (.*?)="
                                                    @close-delim))
                                   match)]
              (do
                (apply set-delims (rest delim-change))
                (.delete builder match-start match-end)
                (recur match-start))
              (if-let [tag (re-find
                            (re-pattern (str @open-delim "(.*?)" @close-delim))
                            match)]
                (do
                  (.replace builder match-start match-end
                            (str "{{" (second tag) "}}"))
                  (recur match-end))))))))
  (.toString builder)))

(defn render
  "Renders the template with the data."
  [template data]
  (let [replacements (create-variable-replacements data)
        template (remove-comments (process-set-delimiters template))
        section (extract-section template)]
    (if (nil? section)
      (remove-all-tags (replace-all template replacements))
      (let [before (.substring template 0 (:start section))
            after (.substring template (:end section))
            section-data ((keyword (:name section)) data)]
        (recur
         (str before
              (if (:inverted section)
                (if (or (and (vector? section-data) (empty? section-data))
                        (not section-data))
                  (:body section))
                (if section-data
                  (let [section-data (if (or (sequential? section-data)
                                             (map? section-data))
                                       section-data {})
                        section-data (if (sequential? section-data) section-data
                                         [section-data])]
                    (map-str (fn [m]
                               (render (:body section) m)) section-data))))
              after) data)))))
