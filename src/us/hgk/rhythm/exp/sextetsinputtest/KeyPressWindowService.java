/* * * * *
 * Copyright © 2016 Peter S. May
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the
 * following conditions:
 * 
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE
 * USE OR OTHER DEALINGS IN THE SOFTWARE.
 * * * * */

package us.hgk.rhythm.exp.sextetsinputtest;

import java.awt.BorderLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.logging.Logger;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import com.google.common.util.concurrent.AbstractIdleService;

public class KeyPressWindowService extends AbstractIdleService {
	private static final Logger log = Logger.getLogger(KeyPressWindowService.class.getName());

	private JFrame frame;
	private JPanel panel;
	private JLabel label;
	private KeyListener kl;
	private WindowListener wl;

	public KeyPressWindowService(final Main main) {
		kl = new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				main.keyUpdate(e.getKeyCode(), true);
			}

			@Override
			public void keyReleased(KeyEvent e) {
				main.keyUpdate(e.getKeyCode(), false);
			}
		};

		wl = new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				main.windowClosing();
			}
		};
	}

	@Override
	protected void startUp() throws Exception {
		log.finer("Starting up KPW");
		
		String titleText = "Sextets Input Demo";
		String labelInitialText = "Press and release keys to change the state.";
		int initialWidth = 600;
		int initialHeight = 100;

		label = setUpLabel(labelInitialText);
		panel = setUpPanel(label);
		panel.addKeyListener(kl);
		frame = setUpFrame(panel, titleText, initialWidth, initialHeight);
		frame.addWindowListener(wl);
		
		frame.setVisible(true);
		
		log.finer("KPW setup OK");
	}

	@Override
	protected void shutDown() throws Exception {
		log.finer("Shutting down KPW");
		
		frame.setVisible(false);
		frame.dispose();
		frame = null;
		panel = null;
		label = null;
		
		log.finer("KPW shutdown OK");
	}

	private static JLabel setUpLabel(String labelInitialText) {
		JLabel label = new JLabel(labelInitialText);
		label.setHorizontalAlignment(JLabel.LEFT);
		label.setVerticalAlignment(JLabel.TOP);
		return label;
	}

	private static JPanel setUpPanel(JLabel label) {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setFocusable(true);
		panel.add(label);
		return panel;
	}

	private static JFrame setUpFrame(JPanel panel, String titleText, int initialWidth, int initialHeight) {
		JFrame frame = new JFrame(titleText);
		frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		frame.add(panel);
		frame.setSize(initialWidth, initialHeight);
		return frame;
	}

	public void setLabelText(String string) {
		if(isRunning()) {
			label.setText(string);
		}
	}

}
