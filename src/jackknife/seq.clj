(ns jackknife.seq
  (:refer-clojure :exclude [flatten memoize])
  (:use [jackknife.core :only (safe-assert)]
        [clojure.set :only (difference)])
  (:require [clojure.walk :refer (postwalk)])
  (:import [java.util List]))

(defn all-pairs
  "[1 2 3] -> [[1 2] [1 3] [2 3]]"
  [coll]
  (let [pair-up (fn [v vals]
                  (map (partial vector v) vals))]
    (apply concat (for [i (range (dec (count coll)))]
                    (pair-up (nth coll i) (drop (inc i) coll))))))

(defn multi-set
  "Returns a map of elem to count"
  [aseq]
  (apply merge-with +
         (map #(hash-map % 1) aseq)))

(defn reverse-map
  "{:a 1 :b 1 :c 2} -> {1 [:a :b] 2 :c}"
  [amap]
  (reduce (fn [m [k v]]
            (let [existing (get m v [])]
              (assoc m v (conj existing k))))
          {} amap))

(defn count= [& args]
  (apply = (map count args)))

(def not-count=
  (complement count=))

(defn flatten
  "Flattens out a nested sequence. unlike clojure.core/flatten, also
  flattens maps."
  [vars]
  (->> vars
       (postwalk #(if (map? %) (seq %) %))
       (clojure.core/flatten)))

(defn repeat-seq
  [amt aseq]
  (apply concat (repeat amt aseq)))

(defn find-first
  "Returns the first item of coll for which (pred item) returns logical true.
  Consumes sequences up to the first match, will consume the entire sequence
  and return nil if no match is found."
  [pred coll]
  (first (filter pred coll)))

(defn remove-first
  [pred coll]
  (let [i (map-indexed vector coll)
        ri (find-first #(pred (second %)) i)]
    (safe-assert ri "Couldn't find an item to remove")
    (map second (remove (partial = ri) i))))

(defn drop-until [pred xs]
  (drop-while (complement pred) xs))

(def ^{:doc "Accepts a predicate and a sequence, and returns:

   [(filter pred xs) (remove pred xs)]"}
  separate
  (juxt filter remove))

(defn wipe
  "Returns a new collection generated by dropping the item at position
  `idx` from `coll`."
  [idx coll]
  (concat (take idx coll) (drop (inc idx) coll)))

(defn prioritize [pred coll]
  (apply concat (separate pred coll)))

(defn merge-to-vec
  "Returns a vector representation of the union of all supplied
  items. Entries in xs can be collections or individual items. For
  example,

  (merge-to-vec [1 2] :help 2 1)
  => [1 2 :help]"
  [& xs]
  (->> xs
       (map #(if (coll? %) (set %) #{%}))
       (reduce #(concat % (difference %2 (set %))))
       (vec)))

(defn transpose [m]
  (apply map list m))

(defn wipe
  "Returns a new collection generated by dropping the item at position
  `idx` from `coll`."
  [idx coll]
  (concat (take idx coll)
          (drop (inc idx) coll)))

(defn collectify [obj]
  (if (or (sequential? obj)
          (instance? List obj))
    obj, [obj]))

(defn unweave
  "[1 2 3 4 5 6] -> [[1 3 5] [2 4 6]]"
  [coll]
  {:pre [(even? (count coll))]}
  [(take-nth 2 coll) (take-nth 2 (rest coll))])

(defn duplicates
  "Returns a vector of all values for which duplicates appear in the
  supplied collection. For example:

  (duplicates [1 2 2 1 3])
  ;=> [1 2]"
  [coll]
  (loop [[x & more] coll, test-set #{}, dups #{}]
    (if-not x
      (vec dups)
      (recur more
             (conj test-set x)
             (if (test-set x) (conj dups x) dups)))))

(defn some? [pred coll]
  ((complement nil?) (some pred coll)))

(letfn [(clean-nil-bindings [bindings]
          (let [pairs (partition 2 bindings)]
            (mapcat identity (filter #(first %) pairs))))]

  (defn mk-destructured-seq-map
    "Accepts pairs of bindings and generates a map of replacements to
     make... TODO: More docs."
    [& bindings]
    ;; lhs needs to be symbolified
    (let [bindings (clean-nil-bindings bindings)
          to-sym (fn [s] (if (keyword? s) s (symbol s)))
          [lhs rhs] (unweave bindings)
          lhs (for [l lhs] (if (sequential? l)
                             (vec (map to-sym l))
                             (symbol l)))
          rhs (for [r rhs] (if (sequential? r)
                             (vec r)
                             r))
          destructured (vec (destructure (interleave lhs rhs)))
          syms (first (unweave destructured))
          extract-code (vec (for [s syms] [(str s) s]))]
      (eval
       `(let ~destructured
          (into {} ~extract-code))))))
