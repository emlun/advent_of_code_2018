(ns adventofcode.2018.day22
  (:require clojure.string
            [adventofcode.2018.A* :as A*]
            [adventofcode.2018.util :refer [abs vec-add vec-sub]]
            ))

(defn parse-input [[depth-line target-line]]
  {:depth (read-string (second (clojure.string/split depth-line #"\s+")))
   :target (let [[_ x y] (re-matches #".*:\s+(\d+),(\d+).*" target-line)]
             (mapv read-string [y x]))
   })

(defn geoindex [{:keys [erosion-level target]} [y x]]
  (cond
    (= [y x] [0 0]) 0
    (= [y x] target) 0
    (= 0 y) (* x 16807)
    (= 0 x) (* y 48271)
    :else (* (get-in erosion-level [y (dec x)]) (get-in erosion-level [(dec y) x]))
    ))

(defn erosion-level [cave [y x]]
  (mod (+ (:depth cave) (get-in cave [:geoindex y x])) 20183))

(defn region-type [cave [y x]]
  (mod (get-in cave [:erosion-level y x]) 3))

(defn minimal-cave [cave]
  (assoc cave
         :geoindex []
         :erosion-level []
         :type []
         ))

(defn add-cave-cell [cave y x]
  (as-> cave $
    (update-in $ [:geoindex y] #(conj % (geoindex $ [y x])))
    (update-in $ [:erosion-level y] #(conj % (erosion-level $ [y x])))
    (update-in $ [:type y] #(conj % (region-type $ [y x])))
    ))

(defn expand-cave-y [cave]
  (let [y (count (:type cave))]
    (reduce #(add-cave-cell %1 y %2)
            (-> cave
                (update :geoindex #(conj % []))
                (update :erosion-level #(conj % []))
                (update :type #(conj % []))
                )
            (range 0 (count (first (:type cave))))
            )))

(defn expand-cave-x [cave]
  (let [x (count (first (:type cave)))]
    (reduce #(add-cave-cell %1 %2 x)
            cave
            (range 0 (count (:type cave)))
            )))

(defn expand-cave [cave [target-y target-x]]
  (->> cave
      (iterate expand-cave-y)
      (filter #(= (count (:type %)) (inc target-y)))
      (first)
      (iterate expand-cave-x)
      (filter #(= (count (first (:type %))) (inc target-x)))
      (first)
      ))

(defn compute-cave [{:keys [target] :as cave}]
  (expand-cave (minimal-cave cave) target))

(defn can-enter? [state [tool [y x]]]
  (case [(get-in state [:cave :type y x]) tool]
    [0 :climb] true
    [0 :torch] true
    [0 :neith] false

    [1 :climb] true
    [1 :torch] false
    [1 :neith] true

    [2 :climb] false
    [2 :torch] true
    [2 :neith] true
    ))

(defn move-cost [[fromtool _] [totool _]]
  (if (= fromtool totool) 1 7))

(defn min-remaining-cost [state move]
  (+
   (->> (second move)
        (vec-sub (second (:target state)))
        (map abs)
        (apply +)
        )
   (if (= (first move) (first (:target state))) 0 7)
   ))

(defn next-moves [state [tool pos]]
  (->> [[-1 0] [0 -1] [1 0] [0 1]]
       (map #(vec-add pos %))
       (filter (fn [[y x]]
                 (and (<= 0 y)
                      (<= 0 x)
                      )))
       (map #(vector tool %))
       (concat (map #(vector % pos) (remove #(= tool %) [:climb :torch :neith])))
       (filter #(can-enter? state %))
  ))

(defn start [cave]
  {:cave (expand-cave cave [0 0])
   :A* (A*/start [:torch [0 0]])
   :target [:torch (:target cave)]
   })

(defn expand-cave-if-needed [state [_ [y x]]]
  (cond-> state
    (= y (dec (count (:type (:cave state)))))
    (update :cave expand-cave-y)

    (= x (dec (count (first (:type (:cave state))))))
    (update :cave expand-cave-x)
    ))

(defn step [state]
  (-> state
      (expand-cave-if-needed (first (A*/next-move (:A* state))))
      (as-> $ (update $ :A* (fn [A*-state]
                                 (A*/step A*-state
                                             (fn [move] (next-moves $ move))
                                             move-cost
                                             (partial min-remaining-cost $)
                                             ))))
      ))

(defn format-cave [cave]
  (->> cave
       (:type)
       (map (fn [row]
              (map (fn [type]
                     (case type
                       0 \.
                       1 \=
                       2 \|
                       ))
                   row
                   )))
       (map clojure.string/join)
       (clojure.string/join "\n")
       ))

(defn format-route-costs [state tool]
  (->> (get-in state [:cave :type])
       (map-indexed (fn [y row]
                      (map-indexed (fn [x type]
                                     (let [cost (get-in state [:A* :route-costs [tool [y x]]])
                                           cost-digit (if cost (mod cost 10) ".")
                                           ]
                                       cost-digit
                                       ))
                                   row
                                   )))
       (map clojure.string/join)
       (clojure.string/join "\n")
       ))

(defn print-state [state]
  (println (format-cave (:cave state)))
  (println)
  (println (->> [:climb :torch :neith]
                (map #(format-route-costs state %))
                (map clojure.string/split-lines)
                (apply map #(clojure.string/join "  " [%1 %2 %3]))
                (clojure.string/join \newline)
                ))
  (println "Move counts:" (map (fn [[k v]] [k (count v)]) (:moves (:A* state))))
  (println "Goal cost:" (A*/get-cost (:A* state) (:target state)))
  )

(defn show-state [cave n]
  (print-state (nth (iterate step (start (minimal-cave cave))) n)))

(defn solve-a [lines]
  (->> lines
       (parse-input)
       (compute-cave)
       (:type)
       (reduce #(apply + %1 %2) 0)
       ))

(defn solve-b [lines]
  (->> lines
       (parse-input)
       (minimal-cave)
       (start)
       (iterate step)
       (some (fn [state]
               (A*/get-cost (:A* state) (:target state))))
       ))

(defn run [input-lines & args]
  {:A (solve-a input-lines)
   :B (solve-b input-lines)
   }
  )

(defn day-lines [] (adventofcode.2018.core/day-lines 22))
