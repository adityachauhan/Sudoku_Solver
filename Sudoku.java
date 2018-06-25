/*
* Sudoku Solver GUI implementation.
* 
* The algorithmused is Pass 2 singletons
* which is basically a parallel solving algorithm
* there are some online PPT and pdf links used to undersatnd the algortihm better.
*
* For coding most of the concepts are based on labs and lectures.
* Though there some online resiurces like 'Tutorials point' and  'java2s.com' are used to understand 
* some coding paradigms and functionalisties.
*
* @author : Aditya Chauhan (University of Sheffield)
* @dated  : 15/01/2018
* @userID : acp17ac
*/

/*
* The input format for text file is as described in the assignment pdf.
* Each line with dashes and numbers without any spaces between them in text file represents a one horizontal row in sudoku.
* According to the code below the file is loaded in such a way that if there are more than 9 elements in one row it will only read first 9.
* If there are less than 9 elememts in one row it will print error in status panel and you can load other file.
* after loading you can press run and it will perform phase1 based on first scan(code explained below).
* After phase 1 done hit press run again to perform phase2 and solve the complete sudoku.
*/

import java.awt.*;
import java.awt.event.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;

import javax.swing.*;

public class Sudoku extends JPanel implements ActionListener {
	
	/*
		Varialbles declaration
		---> Jpanel and Jtextarea for defining the sudoku grid and buttons and status area.
		---> Color  for definig the colors of cells and panels.
	*/
	Thread thread;
	GridBagConstraints gbc = new GridBagConstraints();
	JPanel the_grid = new JPanel();
	JTextArea status_pane = new JTextArea ();
	int board [] [] = new int [9] [9];
	BitSet [] [] bits = new BitSet [9] [9];
	boolean loaded_flag = false, stuck = false, full_rescan = false, phase_one_complete = false;
	int todo = 0, progress = 0;
	int me_x = 0, me_y = 0;
	Color saved_bg;
	Color my_blue = new Color (200, 200, 255);
	Color my_green = new Color (200, 255, 200);
	Color my_red = new Color (255, 180, 180);
	Color my_yellow = new Color(240, 230, 140);
	int debug_level = 0;
	static final boolean DEBUG = false;
	static JFrame the_frame;		// So we can get hold and edit title.

	/*
		Sudoku() is the complete GUI structure of the buttons, solving grid and status panel
	*/

	public Sudoku() {
		gbc.weightx = 1.0;
		gbc.weighty = 1.0;
		gbc.fill = GridBagConstraints.BOTH;
		ContainerListener listener = new ContainerAdapter() {
			public void componentAdded(ContainerEvent e) {
				Component comp = e.getChild();
				if (comp instanceof JButton)
					((JButton) comp).addActionListener(Sudoku.this);
			}
		};
		addContainerListener(listener);
		JPanel buttonbar = new JPanel();
		buttonbar.addContainerListener(listener);
		gbc.gridwidth = 1;
		gbc.weightx = 1.0;
		// buttons for differnt needed operations for sudoku solver
		addGB(buttonbar,new JButton("Load"),0,0);
		addGB(buttonbar, new JButton("Clear"), 3, 0);
		addGB(buttonbar, new JButton("Interrupt"), 2, 0);
		addGB(buttonbar, new JButton("Quit"), 4, 0);
		addGB(buttonbar, new JButton("Run"), 1, 0);
		gbc.gridwidth = 9;
		addGB(this, buttonbar, 0, 1);
		JTextField throw_away = new JTextField();
		saved_bg = throw_away.getBackground();
		// loops setup to make a sudoku grid of 3*3 with each cell having 3*3 cells in it.
		for (int tile_y = 0; tile_y < 3; tile_y++) 
			for (int tile_x = 0; tile_x < 3; tile_x++) {
				JPanel tile = new JPanel();
				tile.setBorder(BorderFactory.createRaisedBevelBorder());
				gbc.weightx = 1.0;
				gbc.gridwidth = 1;
				int text_ct = 0;

				for (int i = 0; i < 3; i++) 
					for (int j = 0; j < 3; j++) {
						JTextField text = new JTextField(1);
						if (DEBUG) {
							text.setText(String.valueOf(text_ct));
							text_ct++;
						}
						text.setInputVerifier( new InputVerifier() {
							public boolean verify( JComponent comp ) {
								JTextField field = (JTextField)comp;
								boolean passed = false;
								try {
									String value = field.getText();
									if (value.equals("") )return true; 
									int n = Integer.parseInt(value);
									if (n == 0) field.setText("");
									passed = ( 0 <= n && n <= 9 );
								} catch (NumberFormatException e) { }
								if ( !passed ) {
									comp.getToolkit().beep();
									field.selectAll();
								}
								return passed;
							}
						} );
						
						addGB(tile, text, j, i);
					}
				addGB(the_grid, tile, tile_x, tile_y);
			}
		addGB(this, the_grid, 0, 2);
		gbc.weighty = 5;

		// setup fot status pane with scrollable text area.

		addGB(this, (new JScrollPane (status_pane)), 0, 3);
		status_pane.setBackground(my_blue);
		status_pane.append("Ready. \n");
	}

	// function to add buttons, text area grids and scrollable text area.

	void addGB(Container cont, Component comp, int x, int y) {
		if ((cont.getLayout() instanceof GridBagLayout) == false)
			cont.setLayout(new GridBagLayout());
		gbc.gridx = x;
		gbc.gridy = y;
		cont.add(comp, gbc);
	}

	// function to perform action when a p[articular button is pressed.
	public void actionPerformed(ActionEvent e) {
		
		if (e.getActionCommand().equals("Clear")) do_clear();
		else if (e.getActionCommand().equals("Load")) load_puzzle();
		else if (e.getActionCommand().equals("Quit")) System.exit(0);
		else if (e.getActionCommand().equals("Run")) do_run();
		else if (e.getActionCommand().equals("Interrupt")) do_interrupt();
	}

	// function to interrupt a programme in between.

	private void do_interrupt (){
		status_pane.append("Interrupted...\n");
		status_pane.append("Press run to resume\n");
		thread.interrupt();


	}
	
	// funtion to setup the values background based on input and setup flags for the inputs on which cell has value and which dont.
	private void do_setup ()  {
		todo = 0;
		me_x = 0;
		me_y = 0;
	
		for (int tile_ct = 0; tile_ct < 9; tile_ct++) {
			Component tile = the_grid.getComponent(tile_ct);
			//thread.sleep(1000);
			
			for (int text_ct = 0; text_ct < 9; text_ct++) {	
				Component text = ((Container) tile).getComponent(text_ct);
				int intval, x, y;
	
				x = board_x_from_tile (tile_ct, text_ct);
				y = board_y_from_tile (tile_ct, text_ct);
				
				if (debug_level > 9) {
					int new_text = text_from_board (x, y);
					int new_tile = tile_from_board (x, y);
				
					status_pane.append( String.format("tile: %d, text: %d, x: %d, y: %d, new_tile: %d, new_text: %d\n",
						tile_ct, text_ct, x, y, new_tile, new_text));
				}
				
			    String value = ((JTextField) text).getText();
			    if (value.equals("")) {
			    	intval = 0;
			    	todo++;
			    }
			    else intval = Integer.parseInt(value);
			    board [x] [y] = intval;
			    BitSet this_bit = new BitSet(10);
			    this_bit.set(intval);
			    bits [x] [y] = this_bit;
			    
			    if (intval > 0) {
			    	((JTextField) text).setBackground(my_yellow);
			    }
			}
		}
		perform_validation ();
		loaded_flag = true;
		stuck = false;
		full_rescan = false;
		phase_one_complete = false;
		progress = todo;	// Initialize progress made.
		if (DEBUG) print_board ();
	}
	int count = 0;

	// function to clear off the sudoku solver grids.

	private void do_clear() {
		Component [] panels = the_grid.getComponents();
		for (Component c : panels) {
			Component[] texts = ((Container) c).getComponents();
			for (Component t :texts ) {
				((JTextField) t).setText("");
				((JTextField) t).setBackground(saved_bg);
			}
		}
		loaded_flag = false;
		count = 0;
	}

	private void do_step() {
		if (loaded_flag == false) do_setup();
		if (stuck) return;
		BitSet me = bits [me_x] [me_y];
		while (me.get(0) == false)  {
			increment_me(); 	// Skip ones already set and keep going.
			me = bits [me_x] [me_y];
		}
		perform_scan (me);
		if (DEBUG) print_board();
		increment_me ();
	}


	// a basic scan to check a missing value that can be set straightforward without setting possible values in other cells

	private void do_phase_1() throws InterruptedException{
		
			if (loaded_flag == false) do_setup();	// Need this to initialize todo and stuck flags.
			while ((todo > 0) && (stuck != true)) {
				do_step();
				count++;
			}
		
		phase_one_complete = true;
	}

	/* a function to set a remaining values after first scan by setting flags 
	for filled values and setting possible values in a cells to fill the best possible value
	*/ 

	private void do_phase_2() throws InterruptedException{
		
			if (loaded_flag == false) do_setup();	// Need this to initialize todo and stuck flags.
			if (phase_one_complete == false) {
				
				do_phase_1();
			}
			find_singletons();
		// Reset me_x and me_y so we're ready to step or run.
			me_x = 0;
			me_y = 0;
		
	}
	// a function to run the loaded sudoku file anad get a result.
	// first run button press runs the first phase 
	// after phase one is done  press run agian for phase two and complete solving of sudoku

	private void do_run(){

		try{
			if(count < 1) {
				status_pane.append("Working....\n");
				do_phase_1();
				thread.sleep(1000);
				status_pane.append("First scan is done missing elements are updated\n");
				status_pane.append("Press Run again.\n");
			}
			else {
				status_pane.append("Working...\n");
				do_phase_2();
				thread.sleep(1000);
				status_pane.append("Done...\n");
			}
		}
		catch (InterruptedException inter){
			status_pane.append("Interrupted...\n");
		}
		thread.start();
	}

	/*
	Function loads the puzzle text file 
	*/

	private void load_puzzle() {
		JFileChooser chooser = new JFileChooser();
		int result = chooser.showOpenDialog(this);
		if (result == JFileChooser.CANCEL_OPTION)
			return;
		try {
			File file = chooser.getSelectedFile();
			String my_name = file.getName();
			the_frame.setTitle(my_name);
			FileReader fr = new FileReader (file);
			BufferedReader in = new BufferedReader (fr);
			String line;
			Component [] panels = the_grid.getComponents();
			String[] array = new String[81];
			int a = 0;
			for(int i=0;i<9;i++){
				line = in.readLine();
				int j=0,k=0;
				for(k=0;k<9;k++){
					String value = new String(line.substring(j,j+1));
					if(value.equals("_")) array[a] = "0";
					else array[a] = value;
					a++;
					j++;
				}
			}
			String[] array_1 = new String[9];
			String[] array_2 = new String[9];
			String[] array_3 = new String[9];
			String[] array_4 = new String[9];
			String[] array_5 = new String[9];
			String[] array_6 = new String[9];
			String[] array_7 = new String[9];
			String[] array_8 = new String[9];
			String[] array_9 = new String[9];

			array_1[0] = array[0]; array_1[1] = array[1]; array_1[2] = array[2]; array_1[3] = array[9]; array_1[4] = array[10]; array_1[5] = array[11]; array_1[6]= array[18];array_1[7] = array[19];array_1[8]= array[20];
			array_2[0] = array[3]; array_2[1] = array[4]; array_2[2] = array[5]; array_2[3] = array[12]; array_2[4] = array[13]; array_2[5] = array[14]; array_2[6]= array[21];array_2[7] = array[22];array_2[8]= array[23];
			array_3[0] = array[6]; array_3[1] = array[7]; array_3[2] = array[8]; array_3[3] = array[15]; array_3[4] = array[16]; array_3[5] = array[17]; array_3[6]= array[24];array_3[7] = array[25];array_3[8]= array[26];
			array_4[0] = array[27]; array_4[1] = array[28]; array_4[2] = array[29]; array_4[3] = array[36]; array_4[4] = array[37]; array_4[5] = array[38]; array_4[6]= array[45];array_4[7] = array[46];array_4[8]= array[47];
			array_5[0] = array[30]; array_5[1] = array[31]; array_5[2] = array[32]; array_5[3] = array[39]; array_5[4] = array[40]; array_5[5] = array[41]; array_5[6]= array[48];array_5[7] = array[49];array_5[8]= array[50];
			array_6[0] = array[33]; array_6[1] = array[34]; array_6[2] = array[35]; array_6[3] = array[42]; array_6[4] = array[43]; array_6[5] = array[44]; array_6[6]= array[51];array_6[7] = array[52];array_6[8]= array[53];
			array_7[0] = array[54]; array_7[1] = array[55]; array_7[2] = array[56]; array_7[3] = array[63]; array_7[4] = array[64]; array_7[5] = array[65]; array_7[6]= array[72];array_7[7] = array[73];array_7[8]= array[74];
			array_8[0] = array[57]; array_8[1] = array[58]; array_8[2] = array[59]; array_8[3] = array[66]; array_8[4] = array[67]; array_8[5] = array[68]; array_8[6]= array[75];array_8[7] = array[76];array_8[8]= array[77];
			array_9[0] = array[60]; array_9[1] = array[61]; array_9[2] = array[62]; array_9[3] = array[69]; array_9[4] = array[70]; array_9[5] = array[71]; array_9[6]= array[78];array_9[7] = array[79];array_9[8]= array[80];

			/*for(int l=0;l<81;l++){
				status_pane.append(array[l]);
			}*/

			int count_comp =0;

			for (Component c : panels) {
				Component[] texts = ((Container) c).getComponents();
				//line = in.readLine();
				//if (line == null) throw new IOException ("Premature EOF");
				//int i = 0;
				if(count_comp == 0){
					for(int b=0;b<9;b++){
						if(array_1[b].equals("0")) ((JTextField) texts[b]).setText("");
						else {
							((JTextField) texts[b]).setText(array_1[b]);
							((JTextField) texts[b]).setBackground(saved_bg);
						}
					}
				}
				if(count_comp ==1){
					for(int b=0;b<9;b++){
						if(array_2[b].equals("0")) ((JTextField) texts[b]).setText("");
						else {
							((JTextField) texts[b]).setText(array_2[b]);
							((JTextField) texts[b]).setBackground(saved_bg);
						}
					}
				}
				if(count_comp==2){
					for(int b=0;b<9;b++){
						if(array_3[b].equals("0")) ((JTextField) texts[b]).setText("");
						else {
							((JTextField) texts[b]).setText(array_3[b]);
							((JTextField) texts[b]).setBackground(saved_bg);
						}
					}
				}
				if(count_comp==3){
					for(int b=0;b<9;b++){
						if(array_4[b].equals("0")) ((JTextField) texts[b]).setText("");
						else {
							((JTextField) texts[b]).setText(array_4[b]);
							((JTextField) texts[b]).setBackground(saved_bg);
						}
					}
				}
				if(count_comp==4){
					for(int b=0;b<9;b++){
						if(array_5[b].equals("0")) ((JTextField) texts[b]).setText("");
						else {
							((JTextField) texts[b]).setText(array_5[b]);
							((JTextField) texts[b]).setBackground(saved_bg);
						}
					}
				}
				if(count_comp==5){
					for(int b=0;b<9;b++){
						if(array_6[b].equals("0")) ((JTextField) texts[b]).setText("");
						else {
							((JTextField) texts[b]).setText(array_6[b]);
							((JTextField) texts[b]).setBackground(saved_bg);
						}
					}
				}
				if(count_comp==6){
					for(int b=0;b<9;b++){
						if(array_7[b].equals("0")) ((JTextField) texts[b]).setText("");
						else {
							((JTextField) texts[b]).setText(array_7[b]);
							((JTextField) texts[b]).setBackground(saved_bg);
						}
					}
				}
				else if(count_comp==7){
					for(int b=0;b<9;b++){
						if(array_8[b].equals("0")) ((JTextField) texts[b]).setText("");
						else {
							((JTextField) texts[b]).setText(array_8[b]);
							((JTextField) texts[b]).setBackground(saved_bg);
						}
					}
				}
				if(count_comp==8){
					for(int b=0;b<9;b++){
						if(array_9[b].equals("0")) ((JTextField) texts[b]).setText("");
						else {
							((JTextField) texts[b]).setText(array_9[b]);
							((JTextField) texts[b]).setBackground(saved_bg);
						}
					}
				}
				count_comp++;
				
			}
			status_pane.append ("Loaded file: " + file + "\n");
			loaded_flag = false;
		} catch (Exception e) {
			status_pane.append ("Could not load file: " + e + "\n");
		}
	}

	// function checks if there is a conflict in values of the cells i.e if there is a value that is not supposed to be there and put red background to it.

	private void flag_conflict(int x, int y) {
		Component tile = the_grid.getComponent(tile_from_board(x, y));
		JTextField text = (JTextField) ((Container) tile)
				.getComponent(text_from_board(x, y));
		text.setBackground(my_red);
	}
	
	// function to set new value in the cells

	private void set_new_value(BitSet me) {
		int value = me.nextClearBit(0);
		board[me_x][me_y] = value;
		if (debug_level > 7) {
			status_pane.append( String.format("Setting new value : %d, at: x = %d, y = %d, with me = ", 
					value, me_x, me_y));
			status_pane.append( String.valueOf (me) + "\n");
		}
		me.flip(0, 10);
		Component tile = the_grid.getComponent(tile_from_board(me_x, me_y));
		JTextField text = (JTextField) ((Container) tile)
				.getComponent(text_from_board(me_x, me_y));
		text.setText(String.valueOf(value));
		text.setBackground(my_green);
		// text.setEditable(false);
		todo--;
	}

	// a fucntion to increase the cell number one foe fitting possible values in it.

	private void increment_me (){
		me_x++;
		if (me_x > 8) {
			me_x =0;
			me_y++;
		}
		if (me_y > 8) {
			me_y = 0;
			if (todo == progress) {
				status_pane.append ("Stuck.\n");
				stuck = true;
				full_rescan = true; 	// Stop optimizing rescan. Hereafter all algorithms will need a full-rescan.
			} else {
				progress = todo; 	// Record our progress.
				status_pane.append ("Loop!\n");
				
			}
		}
	}
	
	/*
	 * Scan row, column, tile centered on me_x, me_y.
	 * 
	 * Modifies bits[me_x][me_y].
	 * Can also modify board and text views if set_new_value is called.
	 * Signals conflict if cardinality is 10.
	 */
	
	private void perform_scan (BitSet me) {
		// row
		for (int x = 0; x < 9; x ++) {
			BitSet target = bits [x] [me_y];
			if (target.get(0) == false) me.or(target); // Bit 0 is a constraining value.
		}
		// column
		for (int y = 0; y < 9; y ++) {
			BitSet target = bits [me_x] [y];
			if (target.get(0) == false) me.or(target);
		}
		// tile
		int tile = tile_from_board (me_x, me_y);
		for (int text = 0; text < 9; text ++) {
			BitSet target = bits [board_x_from_tile(tile, text)] [board_y_from_tile (tile, text)];
			if (target.get(0) == false) me.or(target);
		}
		if (me.cardinality() == 9) {
			set_new_value(me);
			perform_rescan ();	// Rescan row, column, tile up to here.  If we recurse, that's ok.
		}
		if (me.cardinality() == 10)
			flag_conflict (me_x, me_y);
		if (debug_level > 2)
			status_pane.append( String.valueOf (me) + "\n");
	}

	/*
	 * Rescan the row, column, tile up to me_x, me_y.
	 * 
	 * Modifies bits[me_x][me_y]
	 * Saves and restores me_x, me_y because it fakes out the call to perform_scan.
	 */
	private void perform_rescan () {
		int save_me_x = me_x;
		int save_me_y = me_y;
		
		int last_x, last_y, last_text;
		int save_text = text_from_board (save_me_x, save_me_y);
		
		if (full_rescan) {
			last_x = 9;
			last_y = 9;
			last_text = 9;
		} else {
			last_x = save_me_x;
			last_y = save_me_y;
			last_text = save_text;
		}
		
		// Rescan the row at me_y up to me_x or 9 if a full rescan.
		// Scribble on me_x.
		for (int col = 0; col < last_x; col++) {
			if (col == save_me_x) continue;		// In full scan case, don't compare against self.
			me_x = col;
			BitSet me = bits [me_x] [me_y];
			if (me.get(0) == true) perform_scan (me);
		}
		// Rescan the column at me_x up to me_y or 9 if a full rescan.
		me_x = save_me_x;
		for (int row = 0; row < last_y; row ++) {
			if (row == save_me_y) continue;		// In full scan case, don't compare against self.
			me_y = row;
			BitSet me = bits [me_x] [me_y];
			if (me.get(0) == true) perform_scan (me);
		}
		// Rescan the tile holding me, up to me_x, me_y.
		int tile = tile_from_board (save_me_x, save_me_y);
		for (int text = 0; text < last_text; text ++) {
			if (text == save_text) continue;		// In full scan case, don't compare against self.
			me_x = board_x_from_tile (tile, text);
			me_y = board_y_from_tile (tile, text);
			BitSet me = bits [me_x] [me_y];
			if (me.get(0) == true) perform_scan (me);
		}
		// Restore me_x, and me_y.
		me_x = save_me_x;
		me_y = save_me_y;
	}
	
	private boolean set_singleton_value (int x, int y, int number) {
		BitSet me = bits [x] [y];
		if (me.get(0) == false) return false;	// Skip constrained values.
		if (me.get(number) == false) {  // This is the one we want.
			me_x = x;
			me_y = y;
			if (debug_level > 2) {
				status_pane.append( String.format("Setting value %d at: %d, %d.\n", number, x, y));
			}
			me.set(0,10);
			me.clear(number);
			set_new_value(me);
			perform_rescan();			// Propagate the new constraints!
			return true;					// Signal we're done.
		}
		return false;
	}
	
	
	/*
	 * Find Singletons in Rows, Columns, Tiles.
	 * 
	 * Modifies bits[][], me_x, me_y
	 * 
	 */
	
	private void find_singletons () {
		int frequency [] = new int [10];	
		progress = todo;					// Take note of our progress.

		// Do Columns.
		for (int col = 0; col < 9; col++) {
			clear_freqs (frequency);
			for (int row = 0; row < 9; row++) {			// Count frequency of unconstrained value possibilities in row.
				BitSet me = bits [col] [row];
				if (me.get(0) == false) continue;	// Skip constrained values.
				for (int number = 1; number < 10; number++) {
					if (me.get(number) == false) frequency [number]++;	// False means it could be this number.
				}
			}			
			// Any singletons?
			for (int number = 1; number < 10; number++) {
				
				if (frequency[number] == 1)	{		
					// We can do multiple numbers here, but only because we're self consistent.
					// A re-scan is necessary to make sure we don't try and assert a newly constrained value.
					// Find out which cell in the column has our desired number.
					for (int row = 0; row < 9; row++) {
						if (set_singleton_value (col, row, number)) break;
					}
				}
			}
		}
		// Do Rows.
		for (int row = 0; row < 9; row++) {
			clear_freqs (frequency);
			for (int col = 0; col < 9; col++) {			// Count frequency of unconstrained value possibilities in row.
				BitSet me = bits [col] [row];
				if (debug_level > 3) {
					status_pane.append( String.format("col: %d, row: %d, me: ", col, row));
					status_pane.append( String.valueOf (me) + "\n");
				}
				if (me.get(0) == false) continue;	// Skip constrained values.
				for (int number = 1; number < 10; number++) {
					if (me.get(number) == false) frequency [number]++;	// False means it could be this number.
				}
			}
			// Any singletons?
			for (int number = 1; number < 10; number++) {
				if (frequency[number] == 1)	{		
					// Find out which cell in the row has our desired number.
					for (int col = 0; col < 9; col++) {
						if (set_singleton_value (col, row, number)) break;
					}
				}
			}
		}
		
		// Do Tiles.
		for (int tile = 0; tile < 9; tile++) {
			clear_freqs (frequency);
			for (int cell = 0; cell < 9; cell++) {			// Count frequency of unconstrained value possibilities in row.
				me_x = board_x_from_tile (tile, cell);
				me_y = board_y_from_tile (tile, cell);
				BitSet me = bits [me_x] [me_y];
				if (debug_level > 3) {
					status_pane.append( String.format("me_x: %d, me_y: %d, tile: %d, cell: %d, me: ",
							me_x, me_y, tile, cell));
					status_pane.append( String.valueOf (me) + "\n");
				}
				if (me.get(0) == false) continue;	// Skip constrained values.
				for (int number = 1; number < 10; number++) {
					if (me.get(number) == false) frequency [number]++;	// False means it could be this number.
				}
			}			
			// Any singletons?
			for (int number = 1; number < 10; number++) {
				if (frequency[number] == 1)	{		
					// Find out which cell in the tile has our desired number.
					for (int cell = 0; cell < 9; cell++) {
						me_x = board_x_from_tile (tile, cell);
						me_y = board_y_from_tile (tile, cell);
						if (set_singleton_value (me_x, me_y, number)) break;
					}
				}
			}
		}
		if (progress != todo) find_singletons();	// We added new constraints. recurssive to rescan.		
	}
	
	private void clear_freqs (int [] frequency) {
		for (int i = 0; i < 10; i++) frequency[i] = 0;
	}
	
	// just a simple debug method created while writing the code to see the solved sudoku.
	private void print_board() {
		for (int y = 0; y < 9; y++) {
			String line = "";
			for (int x = 0; x < 9; x++) {
				line = line + bits[x][y];
			}
			status_pane.append(line + "\n");
		}
		status_pane.append("----\n");
	}


	private int board_x_from_tile(int tile, int text) {
		return text % 3 + tile % 3 * 3;
	}

	private int board_y_from_tile (int tile, int text) {
		return text / 3 + tile / 3 * 3;
	}
	
	private int text_from_board (int x, int y) {
		return x % 3 + y % 3 * 3;
	}
	
	private int tile_from_board (int x, int y) {
		return x / 3 + y / 3 * 3;
	}
	
	private void perform_validation () {
		// For each hand-set element in the board (bit 0 is false)
		// Look at the other hand-set elements of the row, column and tile.
		// Confirm no others match this one.

		for (int x = 0; x < 9; x++) {
			for (int y =0; y < 9; y++) {
				BitSet me = bits [x][y];
				if (me.get(0) == false) {	// A bound value look scan neighbors					
					// Scan the row.
					for (int col = 0; col < 9; col++) {
						if (col == y) continue;	// Do not compare ourself with ourself.
						BitSet target = bits [x] [col];
						if (me.equals(target)) flag_conflict (x,y);
					}
					// Scan the column.
					for (int row = 0; row < 9; row ++) {
						if (row == x) continue; // Do not compare ourself with ourself.
						BitSet target = bits [row] [y];
						if (me.equals(target)) flag_conflict (x,y);
					}
					// Scan the tile.
					int tile = tile_from_board (x, y);
					for (int text = 0; text < 9; text ++) {
						int tx = board_x_from_tile (tile, text);
						int ty = board_y_from_tile (tile, text);
						if ((tx == x) && (ty == y)) continue;
						BitSet target = bits [tx] [ty];
						if (me.equals(target)) flag_conflict (x,y);
					}
				}
			}
		}
	}
	

	
	public static void main(String[] args) {
		
		the_frame = new JFrame("Sudoku Solver");
		Set<AWTKeyStroke> old = the_frame.getFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS);
		HashSet<AWTKeyStroke> keys = new HashSet<AWTKeyStroke> (old);
		AWTKeyStroke ks = AWTKeyStroke.getAWTKeyStroke(KeyEvent.VK_RIGHT, 0, true);
		keys.add(ks);
		the_frame.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, keys);
		old = the_frame.getFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS);
		keys = new HashSet<AWTKeyStroke> (old);
		ks = AWTKeyStroke.getAWTKeyStroke(KeyEvent.VK_LEFT, 0, true);
		keys.add(ks);
		the_frame.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, keys);
		the_frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		the_frame.setSize(400, 550);
		the_frame.setLocation(200, 200);
		the_frame.setContentPane(new Sudoku());
		the_frame.setVisible(true);
	}
}