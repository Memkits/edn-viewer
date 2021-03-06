
(ns edn-tree-viewer.core
  (:require [hsl.core :refer [hsl]]
            [respo-ui.core :as ui]
            [respo.core
             :refer
             [defcomp defeffect <> >> list-> div button textarea span input style pre code]]
            [respo.comp.space :refer [=<]]
            [edn-tree-viewer.config :refer [dev?]]
            [favored-edn.core :refer [write-edn]]))

(defcomp
 comp-literal
 (x)
 (cond
   (string? x)
     (span
      {:inner-text (pr-str x), :style {:color (hsl 170 80 60), :font-family ui/font-code}})
   (boolean? x)
     (span {:inner-text (str x), :style {:color (hsl 240 90 50), :font-family ui/font-code}})
   (number? x)
     (span {:inner-text (str x), :style {:color (hsl 0 80 50), :font-family ui/font-code}})
   (keyword? x)
     (span {:inner-text (str x), :style {:color (hsl 200 80 70), :font-family ui/font-code}})
   (symbol? x)
     (span {:inner-text (str x), :style {:color (hsl 300 80 70), :font-family ui/font-code}})
   (set? x)
     (span
      {:inner-text (pr-str x), :style {:color (hsl 120 80 40), :font-family ui/font-code}})
   :else (<> (pr-str x))))

(defn literal? [x] (not (coll? x)))

(defcomp
 comp-preview
 (x)
 (span
  {:style {:color (hsl 0 0 70), :font-family ui/font-code, :font-size 12}}
  (cond
    (literal? x) (comp-literal x)
    (map? x) (<> "Map")
    (vector? x)
      (if (and (every? literal? x) (<= (count x) 5)) (<> (pr-str x)) (<> "Vector"))
    (seq? x) (<> "Seq")
    (set? x) (<> "Set")
    :else (<> (pr-str x)))))

(defcomp
 comp-title
 (x)
 (div {:style {:font-family ui/font-fancy, :color (hsl 0 0 70), :padding "0px 4px"}} (<> x)))

(defcomp
 comp-map-keys
 (data selected on-pick)
 (div
  {:style ui/column}
  (comp-title "Map")
  (list->
   {:style ui/column}
   (->> data
        (map
         (fn [[k v]]
           [k
            (div
             {:style (merge
                      {:cursor :pointer, :padding "2px 8px", :font-size 11}
                      (if (= k selected) {:background-color (hsl 0 0 95)})),
              :class-name "clickable-item",
              :on-click (fn [e d!] (on-pick k d!))}
             (comp-literal k)
             (=< 8 nil)
             (comp-preview v))]))))))

(defcomp
 comp-seq-keys
 (data selected on-pick)
 (div
  {}
  (comp-title (str "Seq of: " (count data)))
  (list->
   {:style ui/column}
   (->> data
        (map-indexed
         (fn [idx item]
           [idx
            (div
             {:on-click (fn [e d!] (on-pick idx d!)),
              :style (merge
                      {:cursor :pointer, :padding "2px 8px"}
                      (if (= idx selected) {:background-color (hsl 0 0 95)}))}
             (comp-literal idx)
             (=< 4 nil)
             (comp-preview item))]))))))

(defcomp
 comp-vector-keys
 (data selected on-pick)
 (div
  {}
  (comp-title (str "Vector of size: " (pr-str (count data))))
  (list->
   {:style ui/column}
   (->> data
        (map-indexed
         (fn [idx item]
           [idx
            (div
             {:style (merge
                      {:cursor :pointer, :padding "2px 8px"}
                      (if (= idx selected) {:background-color (hsl 0 0 95)})),
              :class-name "clickable-item",
              :on-click (fn [e d!] (on-pick idx d!))}
             (comp-literal idx)
             (=< 4 nil)
             (comp-preview item))]))))))

(defn get-by-keys [data xs]
  (cond
    (empty? xs) data
    (nil? data) nil
    (map? data) (recur (get data (first xs)) (rest xs))
    (vector? data) (if (number? (first xs)) (recur (nth data (first xs)) (rest xs)) nil)
    (seq? data) (recur (nth data (first xs)) (rest xs))
    :else nil))

(defcomp
 comp-edn-tree-viewer
 (states data styles)
 (let [cursor (:cursor states), state (or (:data states) {:path []})]
   (div
    {:style (merge ui/column {:max-height "80vh"} styles)}
    (style
     {:innerHTML ".clickable-item:hover {\n  background-color: hsl(0,0%,95%);\n  cursor: pointer;\n}\n",
      :scoped true})
    (list->
     {:style (merge ui/row {:font-size 13})}
     (->> state
          :path
          (map-indexed
           (fn [idx k]
             [idx
              (span
               {:style {:display :inline-block, :padding "0 4px"},
                :class-name "clickable-item",
                :on-click (fn [e d!]
                  (d! cursor (assoc state :path (vec (take (inc idx) (state :path))))))}
               (comp-literal k))]))))
    (list->
     {:style (merge
              ui/expand
              ui/row
              {:overflow :auto, :font-size 13, :border-top (str "1px solid " (hsl 0 0 90))})}
     (concat
      (->> state
           :path
           count
           inc
           range
           (map
            (fn [idx]
              (let [d (get-by-keys data (take idx (state :path)))]
                [idx
                 (div
                  {:style {:padding "4px 0px",
                           :border-left (str "1px solid " (hsl 20 70 90)),
                           :overflow :auto,
                           :flex-shrink 0}}
                  (cond
                    (map? d)
                      (comp-map-keys
                       d
                       (get-in state [:path idx])
                       (fn [result d!]
                         (d!
                          cursor
                          (assoc
                           state
                           :path
                           (-> (take idx (state :path)) vec (conj result))))))
                    (vector? d)
                      (comp-vector-keys
                       d
                       (get-in state [:path idx])
                       (fn [result d!]
                         (d!
                          cursor
                          (assoc
                           state
                           :path
                           (-> (take idx (state :path)) vec (conj result))))))
                    (seq? d)
                      (comp-seq-keys
                       d
                       (get-in state [:path idx])
                       (fn [result d!]
                         (d!
                          cursor
                          (assoc
                           state
                           :path
                           (-> (take idx (state :path)) vec (conj result))))))
                    :else
                      (div
                       {}
                       (comp-title "Literal")
                       (div {:style {:padding "0 6px"}} (comp-literal d)))))]))))
      [[-2
        (div
         {:style (merge
                  ui/expand
                  {:border-left (str "1px solid " (hsl 0 0 90)),
                   :padding "4px 4px",
                   :min-width "max-content",
                   :flex-shrink 0,
                   :white-space :pre,
                   :font-family ui/font-code,
                   :line-height "20px",
                   :padding-bottom 200,
                   :padding-right 80})}
         (code
          {:style {:line-height "16px"},
           :inner-text (write-edn (get-in data (:path state)) {:indent 2})}))]
       [-1 (div {:style {:width 200}})]])))))
